package com.example.testgenerator.planning;

import com.example.testgenerator.analysis.model.CallKind;
import com.example.testgenerator.analysis.model.CatchModel;
import com.example.testgenerator.analysis.model.ClassModel;
import com.example.testgenerator.analysis.model.ConditionModel;
import com.example.testgenerator.analysis.model.MethodCallModel;
import com.example.testgenerator.analysis.model.MethodModel;
import com.example.testgenerator.analysis.model.ParameterModel;
import com.example.testgenerator.analysis.model.ReturnModel;
import com.example.testgenerator.analysis.model.ThrowModel;
import com.example.testgenerator.analysis.model.TryModel;
import com.example.testgenerator.planning.model.*;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class DefaultTestPlanner implements TestPlanner {

    private final DefaultValueResolver valueResolver;

    private final TestDataAssembler testDataAssembler;

    private final MockSetupAssembler mockSetupAssembler;

    public DefaultTestPlanner(DefaultValueResolver valueResolver) {
        this.valueResolver = valueResolver;
        this.testDataAssembler = new TestDataAssembler(valueResolver);
        this.mockSetupAssembler = new MockSetupAssembler(valueResolver);
    }

    // -----------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------

    @Override
    public List<TestScenario> plan(ClassModel classModel) {

        List<TestScenario> scenarios = new ArrayList<>();

        for (MethodModel method : classModel.methods()) {

            scenarios.addAll(planMethod(method));
        }

        return scenarios;
    }

    @Override
    public void configure(Path projectRoot, List<String> imports) {
        valueResolver.configure(
                projectRoot.resolve("src/main/java"),
                imports
        );
    }

    private List<TestScenario> planMethod(MethodModel method) {

        List<TestScenario> scenarios = new ArrayList<>();

        // 1. Guard clauses: top-level ifs that throw and are NOT inside a try.
        //    One scenario per thrown exception.
        for (ConditionModel condition : method.conditions()) {

            if (!isGuardClause(condition)) {
                continue;
            }

            for (String exceptionType : condition.thrownExceptions()) {
                scenarios.add(
                        guardClauseScenario(method, condition, exceptionType)
                );
            }
        }

        // 2. Try/catch scenarios: one per catch clause.
        for (TryModel tryModel : method.tries()) {
            for (CatchModel catchModel : tryModel.catches()) {
                scenarios.add(
                        catchScenario(method, tryModel, catchModel)
                );
            }
        }

        // 3. Happy path scenario. If there are no guard clauses and no
        //    try/catch, this is just a basic scenario.
        scenarios.add(happyPathScenario(method));

        return scenarios;
    }

    private String guardDisplayName(
            MethodModel method,
            ConditionModel condition,
            String exceptionType) {

        String discriminator = discriminatorFor(condition, method);
        return method.name()
               + " should throw " + exceptionType
               + (discriminator.isEmpty() ? "" : " when " + discriminator);
    }

    private String discriminatorFor(
            ConditionModel condition,
            MethodModel method) {

        // 1. Prefer a dependency call that uniquely identifies this condition.
        for (MethodCallModel call : condition.methodCalls()) {
            if (call.kind() == CallKind.DEPENDENCY) {
                return call.target() + "." + call.methodName();
            }
        }

        // 2. Fall back to a parameter name.
        for (ParameterModel p : method.parameters()) {
            if (condition.expression().contains(p.name())) {
                return "parameter " + p.name();
            }
        }

        // 3. Fall back to the condition text.
        String expr = condition.expression().replaceAll("\\s+", " ").trim();
        return expr.length() > 40 ? expr.substring(0, 40) : expr;
    }

    // -----------------------------------------------------------------
    // Guard clause scenarios
    // -----------------------------------------------------------------

    private boolean isGuardClause(ConditionModel condition) {

        // A guard clause throws *and* is not wrapped in a try.
        // We use the condition's own context to decide.
        if (condition.thrownExceptions().isEmpty()) {
            return false;
        }

        // If the condition has no context (older callers), assume top-level.
        if (condition.context() == null) {
            return true;
        }

        return condition.context().tryDepth() == 0
               && !condition.context().insideCatch();
    }

    private TestScenario guardClauseScenario(
            MethodModel method,
            ConditionModel condition,
            String exceptionType) {

        List<MockSetup> setups = new ArrayList<>();

        // Stub every dependency call in the condition with a per-call value
        // that makes that call's branch of the condition evaluate to true.
        for (MethodCallModel call : condition.methodCalls()) {
            if (call.kind() != CallKind.DEPENDENCY) {
                continue;
            }
            setups.add(new MockSetup(
                    call.target(),
                    call.targetType(),
                    call.methodName(),
                    mockSetupAssembler.normalizeArguments(call.arguments(), method),
                    MockAction.RETURN,
                    guardStubValueFor(call, condition.expression())
            ));
        }

        return new TestScenario(
                method.name(),
                guardDisplayName(method, condition, exceptionType),
                method.returnType(),
                method.declaredThrows(),
                method.parameters(),
                testDataAssembler.assemble(method, condition),
                setups,
                new ExpectedOutcome(
                        OutcomeKind.THROW_EXCEPTION,
                        exceptionType
                )
        );
    }

    /**
     * Decide the stub value for a single dependency call so that this call's
     * contribution to the guard condition evaluates to the branch value that
     * makes the guard fire.
     *
     * The guard fires when the condition is TRUE. For a call:
     *   - if it is negated (!call), stub it to false
     *   - if it is not negated (call), stub it to true
     *
     * This is a local rule that assumes the call appears as a top-level
     * conjunct/disjunct of the condition, which covers the shapes we generate
     * today (simple `x`, `!x`, `a && x`, `a || x`).
     */
    private String guardStubValueFor(
            MethodCallModel call,
            String expression) {

        boolean negated = isCallNegated(expression, call);

        // Negated call -> stub false so !false == true.
        // Non-negated call -> stub true.
        return negated ? "false" : "true";
    }

    /**
     * Returns true if the given dependency call appears in the expression
     * with a leading `!`, i.e. the call is negated.
     */
    private boolean isCallNegated(
            String expression,
            MethodCallModel call) {

        String needle = call.target() + "." + call.methodName();
        int idx = expression.indexOf(needle);

        if (idx < 0) {
            return false;
        }

        // Look back a few characters for a `!`, allowing for whitespace and
        // an optional `(` after the `!`.
        int lookbackStart = Math.max(0, idx - 4);
        String prefix = expression.substring(lookbackStart, idx);

        // Match `!` optionally followed by whitespace or `(`.
        return Pattern.compile("!\\s*\\(?\\s*$").matcher(prefix).find();
    }

    /**
     * Decide what stub value makes the given condition evaluate to true.
     * Handles the common shapes; falls back to "true".
     */
    private String stubValueForCondition(String expression) {

        String e = expression.trim();

        // !x.foo(...) -> false
        if (e.startsWith("!")) {
            return "false";
        }

        // x == null -> null
        if (e.endsWith("== null")) {
            return "null";
        }

        // x != null -> some non-null. Renderer will need help here; use
        // a Mockito-wide non-null. For now, a generic mock(Object.class)
        // is only useful as a placeholder; if the arg type is unknown,
        // the renderer should be responsible for choosing something better.
        if (e.endsWith("!= null")) {
            return "mock(Object.class)";
        }

        // x.isEmpty() -> false, x.isPresent() -> true ... leave to the
        // renderer for now; default to true.
        return "true";
    }

    // -----------------------------------------------------------------
    // Try / catch scenarios
    // -----------------------------------------------------------------

    private TestScenario catchScenario(
            MethodModel method,
            TryModel tryModel,
            CatchModel catchModel) {

        List<MockSetup> setups = new ArrayList<>();

        // 0. Identify the call that will be made to throw before we do
        //    anything else, so the pre-try stubbing can skip it.
        MethodCallModel throwingCall = firstDependencyCall(tryModel.bodyCalls());

        // 1. Stub pre-try dependency calls whose results are used later
        //    (e.g. mapper.toEntity(request) -> entity, repository.save(entity) -> saved).
        //    Without these, the service's local variables are null and the
        //    catch body NPEs before reaching the verify.
        setups.addAll(preTryUsedSetups(method, throwingCall));

        // 2. Neutralize every guard clause above the try so execution can
        //    reach the try body.
        setups.addAll(guardNeutralizingSetups(method));

        // 3. Make the first dependency call inside the try throw the caught
        //    exception type.
        if (throwingCall != null) {
            setups.add(new MockSetup(
                    throwingCall.target(),
                    throwingCall.targetType(),
                    throwingCall.methodName(),
                    mockSetupAssembler.normalizeArguments(throwingCall.arguments(), method),
                    MockAction.THROW,
                    catchModel.exceptionType()
            ));
        }

        // 4. Verify the dependency calls in the catch body.
        for (MethodCallModel call : catchModel.methodCalls()) {
            if (call.kind() != CallKind.DEPENDENCY) {
                continue;
            }
            setups.add(new MockSetup(
                    call.target(),
                    call.targetType(),
                    call.methodName(),
                    mockSetupAssembler.normalizeArguments(call.arguments(), method),
                    MockAction.VERIFY,
                    ""
            ));
        }

        // 5. Verify the dependency calls in the finally block, if any.
        for (MethodCallModel call : tryModel.finallyCalls()) {
            if (call.kind() != CallKind.DEPENDENCY) {
                continue;
            }
            setups.add(new MockSetup(
                    call.target(),
                    call.targetType(),
                    call.methodName(),
                    mockSetupAssembler.normalizeArguments(call.arguments(), method),
                    MockAction.VERIFY,
                    ""
            ));
        }

        ExpectedOutcome outcome;
        if (!catchModel.throwsStatements().isEmpty()) {
            ThrowModel t = catchModel.throwsStatements().get(0);
            outcome = new ExpectedOutcome(
                    OutcomeKind.THROW_EXCEPTION,
                    t.exceptionType()
            );
        } else {
            outcome = createNormalExpectedOutcome(method);
        }

        String displayName = method.name()
                             + " should handle "
                             + catchModel.exceptionType();

        return new TestScenario(
                method.name(),
                displayName,
                method.returnType(),
                method.declaredThrows(),
                method.parameters(),
                testDataAssembler.assemble(method, null),
                setups,
                outcome
        );
    }

    /**
     * For every dependency call outside the try (tryDepth == 0, not inside
     * a catch) whose result is assigned to a variable and used later, emit
     * a RETURN stub. This binds the service's local variables to objects the
     * test can refer to, so the catch body has valid data to work with.
     */
    private List<MockSetup> preTryUsedSetups(
            MethodModel method,
            MethodCallModel throwingCall) {

        List<MockSetup> setups = new ArrayList<>();

        for (MethodCallModel call : method.methodCalls()) {

            if (call.kind() != CallKind.DEPENDENCY) continue;

            // Only consider calls outside the try and outside any catch.
            if (call.context() != null) {
                if (call.context().tryDepth() > 0) continue;
                if (call.context().insideCatch()) continue;
            }

            // Skip the call that will be made to throw.
            if (throwingCall != null
                && call.target().equals(throwingCall.target())
                && call.methodName().equals(throwingCall.methodName())) {
                continue;
            }

            // Skip calls whose result is not captured.
            if (!mockSetupAssembler.callResultIsUsed(call, method)) continue;

            // findReturnValue gives the variable name the result is bound to.
            String value = mockSetupAssembler.findReturnValue(call, method);
            if ("null".equals(value)) continue;

            setups.add(new MockSetup(
                    call.target(),
                    call.targetType(),
                    call.methodName(),
                    mockSetupAssembler.normalizeArguments(call.arguments(), method),
                    MockAction.RETURN,
                    value
            ));
        }

        return setups;
    }

    /**
     * For each top-level guard clause (an if that throws and is not inside a
     * try), emit a stub that makes the guard NOT fire. This is what allows
     * execution to reach the try block in catch scenarios.
     */
    private List<MockSetup> guardNeutralizingSetups(MethodModel method) {

        List<MockSetup> setups = new ArrayList<>();

        for (ConditionModel condition : method.conditions()) {

            if (!isGuardClause(condition)) {
                continue;
            }

            if (condition.context() != null) {
                if (condition.context().tryDepth() > 0
                    || condition.context().insideCatch()) {
                    continue;
                }
            }

            // Only the first dependency call in the condition needs to be
            // neutralized — short-circuit evaluation means the rest won't run.
            MethodCallModel firstDependency = firstDependencyCall(condition.methodCalls());
            if (firstDependency == null) {
                continue;
            }

            String stub = negateConditionStub(
                    stubValueForCondition(condition.expression()));

            setups.add(new MockSetup(
                    firstDependency.target(),
                    firstDependency.targetType(),
                    firstDependency.methodName(),
                    mockSetupAssembler.normalizeArguments(firstDependency.arguments(), method),
                    MockAction.RETURN,
                    stub
            ));
        }

        return setups;
    }

    private MethodCallModel firstDependencyCall(List<MethodCallModel> calls) {
        for (MethodCallModel call : calls) {
            if (call.kind() == CallKind.DEPENDENCY) {
                return call;
            }
        }
        return null;
    }

    // -----------------------------------------------------------------
    // Happy path scenario
    // -----------------------------------------------------------------

    private TestScenario happyPathScenario(MethodModel method) {

        List<MockSetup> setups = new ArrayList<>();

        // 1. Neutralize guards.
        setups.addAll(guardNeutralizingSetupsForHappyPath(method));

        // 2. Stub getters on locals that come from dependency-call results.
        Set<String> guardKeys = method.conditions().stream()
                .filter(this::isGuardClause)
                .flatMap(c -> c.methodCalls().stream())
                .map(mockSetupAssembler::callKey)
                .collect(Collectors.toSet());

        setups.addAll(mockSetupAssembler.forResultGetters(method, guardKeys));

        // 3. Stub the dependency calls themselves.
        for (MethodCallModel call : method.methodCalls()) {
            if (call.context() != null && call.context().insideCatch()) continue;
            if (call.kind() != CallKind.DEPENDENCY) continue;
            if (guardKeys.contains(mockSetupAssembler.callKey(call))) continue;

            setups.addAll(mockSetupAssembler.forDependencyCall(call, method));
        }

        return new TestScenario(
                method.name(),
                method.name() + " should execute successfully",
                method.returnType(),
                method.declaredThrows(),
                method.parameters(),
                testDataAssembler.assemble(method, null),
                setups,
                createNormalExpectedOutcome(method)
        );
    }


    private List<MockSetup> guardNeutralizingSetupsForHappyPath(MethodModel method) {

        List<MockSetup> setups = new ArrayList<>();

        for (ConditionModel condition : method.conditions()) {

            if (!isGuardClause(condition)) continue;
            if (condition.context() != null
                && (condition.context().tryDepth() > 0
                    || condition.context().insideCatch())) continue;

            // Find the first dependency call in the condition and make
            // its branch short-circuit the whole condition.
            for (MethodCallModel call : condition.methodCalls()) {
                if (call.kind() != CallKind.DEPENDENCY) continue;

                String stub = isCallNegated(condition.expression(), call)
                        ? "true"    // !call -> stub true so !true = false
                        : "false";  // call -> stub false so false

                setups.add(new MockSetup(
                        call.target(),
                        call.targetType(),
                        call.methodName(),
                        mockSetupAssembler.normalizeArguments(call.arguments(), method),
                        MockAction.RETURN,
                        stub
                ));
                break;  // only the first dependency call
            }
        }

        return setups;
    }

    private String negateConditionStub(String stubValue) {

        if (stubValue == null) return "false";
        return switch (stubValue) {
            case "true" -> "false";
            case "false" -> "true";
            case "null" -> "mock(Object.class)";
            default -> "false";
        };
    }

    // -----------------------------------------------------------------
    // Expected outcome
    // -----------------------------------------------------------------

    private ExpectedOutcome createNormalExpectedOutcome(MethodModel method) {

        if ("void".equals(method.returnType())) {
            return new ExpectedOutcome(OutcomeKind.VOID, "");
        }

        // Optional path — unchanged.
        for (MethodCallModel call : method.methodCalls()) {
            if (TypeValueSupport.isOptionalOrElseThrow(method, call)) {
                return new ExpectedOutcome(
                        OutcomeKind.RETURN_VALUE,
                        TypeValueSupport.expectedVariableName(method)
                );
            }
        }

        if (!method.returns().isEmpty()) {

            ReturnModel returnModel = method.returns().getLast();
            String expr = returnModel.expression().trim();

            // Direct-return-of-local: check identity.
            boolean returnsLocal = method.assignments().stream()
                    .anyMatch(a -> a.variableName().equals(expr));

            // Dependency-call return: unchanged.
            for (MethodCallModel call : method.methodCalls()) {
                if (call.kind() != CallKind.DEPENDENCY) continue;
                String needle = call.target() + "." + call.methodName() + "(";
                if (expr.contains(needle)) {
                    return new ExpectedOutcome(
                            OutcomeKind.RETURN_VALUE,
                            TypeValueSupport.defaultValueFor(method.returnType()),
                            false
                    );
                }
            }

            return new ExpectedOutcome(
                    OutcomeKind.RETURN_VALUE,
                    expr,
                    returnsLocal
            );
        }

        return new ExpectedOutcome(OutcomeKind.VOID, "");
    }

}