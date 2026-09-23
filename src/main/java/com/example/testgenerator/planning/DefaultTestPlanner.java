package com.example.testgenerator.planning;

import com.example.testgenerator.analysis.model.AssignmentModel;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class DefaultTestPlanner implements TestPlanner {

    private final DefaultValueResolver valueResolver;

    public DefaultTestPlanner(DefaultValueResolver valueResolver) {
        this.valueResolver = valueResolver;
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
                    normalizeArguments(call.arguments(), method),
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
                createTestData(method, condition),
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
                    normalizeArguments(throwingCall.arguments(), method),
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
                    normalizeArguments(call.arguments(), method),
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
                createTestData(method, null),
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
            if (!callResultIsUsed(call, method)) continue;

            // findReturnValue gives the variable name the result is bound to.
            String value = findReturnValue(call, method);
            if ("null".equals(value)) continue;

            setups.add(new MockSetup(
                    call.target(),
                    call.targetType(),
                    call.methodName(),
                    normalizeArguments(call.arguments(), method),
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
                    normalizeArguments(firstDependency.arguments(), method),
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

        // Neutralize guards first.
        List<MockSetup> setups = new ArrayList<>(guardNeutralizingSetupsForHappyPath(method));

        // Then handle other dependency calls that aren't guard predicates.
        Set<String> guardKeys = method.conditions().stream()
                .filter(this::isGuardClause)
                .flatMap(c -> c.methodCalls().stream())
                .map(this::callKey)
                .collect(Collectors.toSet());

        for (MethodCallModel call : method.methodCalls()) {
            if (call.context() != null && call.context().insideCatch()) continue;
            if (call.kind() != CallKind.DEPENDENCY) continue;
            if (guardKeys.contains(callKey(call))) continue;

            setups.addAll(createMockSetups(call, method));
        }

        setups.addAll(resultGetterSetups(method, guardKeys));

        return new TestScenario(
                method.name(),
                method.name() + " should execute successfully",
                method.returnType(),
                method.declaredThrows(),
                method.parameters(),
                createTestData(method, null),
                setups,
                createNormalExpectedOutcome(method)
        );
    }

    private List<MockSetup> resultGetterSetups(
            MethodModel method,
            Set<String> alreadyHandledKeys) {

        List<MockSetup> setups = new ArrayList<>();

        // 1. Find locals bound to a dependency call:
        //    <Type> <var> = <dep>.<method>(...)
        Set<String> dependencyResultLocals = new HashSet<>();
        for (AssignmentModel assignment : method.assignments()) {
            for (MethodCallModel call : method.methodCalls()) {
                if (call.kind() != CallKind.DEPENDENCY) continue;

                String needle = call.target() + "." + call.methodName() + "(";
                if (assignment.expression().contains(needle)) {
                    dependencyResultLocals.add(assignment.variableName());
                }
            }
        }

        if (dependencyResultLocals.isEmpty()) {
            return setups;
        }

        // 2. For calls on those locals, stub a return value.
        for (MethodCallModel call : method.methodCalls()) {
            if (!dependencyResultLocals.contains(call.target())) continue;

            // Skip if we've already handled this call elsewhere (avoids
            // duplicating verifies).
            if (alreadyHandledKeys.contains(callKey(call))) continue;

            String value = defaultReturnForGetter(call);
            if (value == null) continue;

            setups.add(new MockSetup(
                    call.target(),
                    call.targetType(),
                    call.methodName(),
                    normalizeArguments(call.arguments(), method),
                    MockAction.RETURN,
                    value
            ));
        }

        return setups;
    }

    /**
     * Return a sensible default literal for a getter-style method, or null
     * if we don't know how to stub it and should skip it.
     */
    private String defaultReturnForGetter(
            MethodCallModel call) {

        String name = call.methodName();

        // Real getters take no arguments. list.get(0), map.get(key), etc.
        // are collection access, not property getters.
        if (!call.arguments().isEmpty()) {
            return null;
        }

        if (name.startsWith("is")) {
            return "true";
        }

        if (name.startsWith("get")) {
            if (name.equals("getMessageId")
                || name.endsWith("Message")
                || name.endsWith("Id")
                || name.endsWith("Name")
                || name.endsWith("Status")) {
                return "\"msg-123\"";
            }
            return "\"test-value\"";
        }

        return null;
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

                String stub = isNegated(condition.expression(), call)
                        ? "true"    // !call -> stub true so !true = false
                        : "false";  // call -> stub false so false

                setups.add(new MockSetup(
                        call.target(),
                        call.targetType(),
                        call.methodName(),
                        normalizeArguments(call.arguments(), method),
                        MockAction.RETURN,
                        stub
                ));
                break;  // only the first dependency call
            }
        }

        return setups;
    }

    private boolean isNegated(String expression, MethodCallModel call) {
        String needle = call.target() + "." + call.methodName();
        int idx = expression.indexOf(needle);
        if (idx <= 0) return false;
        // Look back a few chars for a `!`
        int lookbackStart = Math.max(0, idx - 3);
        return expression.substring(lookbackStart, idx).contains("!");
    }

    private String conditionFor(
            MethodModel method,
            MethodCallModel call) {

        for (ConditionModel condition : method.conditions()) {
            if (condition.methodCalls().stream()
                    .anyMatch(c -> callKey(c).equals(callKey(call)))) {
                return condition.expression();
            }
        }
        return "";
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

    private String callKey(MethodCallModel call) {
        return call.target() + "."
               + call.methodName()
               + call.arguments();
    }

    // -----------------------------------------------------------------
    // Mock setup construction
    // -----------------------------------------------------------------

    private List<MockSetup> createMockSetups(
            MethodCallModel call,
            MethodModel method) {

        List<MockSetup> setups = new ArrayList<>();

        // Always verify dependency calls in the happy path.
        MockSetup verifySetup = new MockSetup(
                call.target(),
                call.targetType(),
                call.methodName(),
                normalizeArguments(call.arguments(), method),
                MockAction.VERIFY,
                ""
        );

        if (isOptionalOrElseThrow(method, call)) {
            setups.add(new MockSetup(
                    call.target(),
                    call.targetType(),
                    call.methodName(),
                    normalizeArguments(call.arguments(), method),
                    MockAction.RETURN,
                    "java.util.Optional.of("
                    + optionalExpectedVariable(method, call)
                    + ")"
            ));
            setups.add(verifySetup);
            return setups;
        }

        String value = findReturnValue(call, method);

        if (!"null".equals(value)) {
            setups.add(new MockSetup(
                    call.target(),
                    call.targetType(),
                    call.methodName(),
                    normalizeArguments(call.arguments(), method),
                    MockAction.RETURN,
                    value
            ));
            setups.add(verifySetup);
            return setups;
        }

        if (callResultIsUsed(call, method)) {
            setups.add(new MockSetup(
                    call.target(),
                    call.targetType(),
                    call.methodName(),
                    normalizeArguments(call.arguments(), method),
                    MockAction.RETURN,
                    defaultValueFor(method.returnType())
            ));
            setups.add(verifySetup);
            return setups;
        }

        // Pure side-effect call: verify only.
        setups.add(verifySetup);
        return setups;
    }

    private List<String> normalizeArguments(
            List<String> arguments,
            MethodModel method) {

        return arguments.stream()
                .map(a -> normalizeMockArgument(a, method))
                .toList();
    }

    private String normalizeMockArgument(
            String argument,
            MethodModel method) {

        return method.assignments().stream()
                .filter(a -> a.variableName().equals(argument))
                .filter(a -> a.expression().startsWith("new "))
                .map(a -> "any(" + a.variableType() + ".class)")
                .findFirst()
                .orElse(argument);
    }

    private boolean callResultIsUsed(
            MethodCallModel call,
            MethodModel method) {

        String needle = call.target() + "." + call.methodName() + "(";

        // Assigned to a variable?
        boolean assigned = method.assignments().stream()
                .anyMatch(a -> a.expression().contains(needle));

        // Returned directly?
        boolean returned = method.returns().stream()
                .anyMatch(r -> r.expression().contains(needle));

        return assigned || returned;
    }

    private String findReturnValue(
            MethodCallModel call,
            MethodModel method) {

        return method.assignments().stream()
                .filter(a -> a.expression().contains(
                        call.target() + "." + call.methodName()))
                .map(AssignmentModel::variableName)
                .findFirst()
                .orElse("null");
    }

    // -----------------------------------------------------------------
    // Expected outcome
    // -----------------------------------------------------------------

    private ExpectedOutcome createNormalExpectedOutcome(MethodModel method) {

        if ("void".equals(method.returnType())) {
            return new ExpectedOutcome(OutcomeKind.VOID, "");
        }

        for (MethodCallModel call : method.methodCalls()) {
            if (isOptionalOrElseThrow(method, call)) {
                return new ExpectedOutcome(
                        OutcomeKind.RETURN_VALUE,
                        optionalExpectedVariable(method, call)
                );
            }
        }

        if (!method.returns().isEmpty()) {

            ReturnModel returnModel = method.returns().getLast();

            // If the return expression is a dependency call, the mock was
            // stubbed with a type-appropriate default; assert against that
            // literal rather than re-invoking the mock.
            for (MethodCallModel call : method.methodCalls()) {
                if (call.kind() != CallKind.DEPENDENCY) continue;

                String needle = call.target() + "." + call.methodName() + "(";
                if (returnModel.expression().contains(needle)) {
                    return new ExpectedOutcome(
                            OutcomeKind.RETURN_VALUE,
                            defaultValueFor(method.returnType())
                    );
                }
            }

            // Non-dependency return (e.g. a field, a computed value).
            return new ExpectedOutcome(
                    OutcomeKind.RETURN_VALUE,
                    returnModel.expression()
            );
        }

        return new ExpectedOutcome(OutcomeKind.VOID, "");
    }

    // -----------------------------------------------------------------
    // Test data
    // -----------------------------------------------------------------

    private List<TestData> createTestData(
            MethodModel method,
            ConditionModel condition) {

        List<TestData> testData = new ArrayList<>();

        // Which parameter does this condition check for null (if any)?
        String nullCheckedParam =
                nullCheckedParameter(condition, method);

        GuardedField guarded = guardedField(condition, method);

        for (ParameterModel parameter : method.parameters()) {
            String initialization;

            if (parameter.name().equals(nullCheckedParam)) {
                initialization = guardTriggeringValue(parameter, condition);
            } else if (guarded != null
                       && guarded.parameterName().equals(parameter.name())) {
                initialization = parameterInitializerWithOverride(
                        parameter,
                        guarded.fieldName(),
                        guarded.value()
                );
            } else {
                initialization = parameterInitializer(parameter);
            }

            testData.add(new TestData(
                    parameter.name(),
                    parameter.type(),
                    initialization
            ));
        }

        for (MethodCallModel call : method.methodCalls()) {
            if (isOptionalOrElseThrow(method, call)) {
                String variableName =
                        optionalExpectedVariable(method, call);

                // The variable holds the UNWRAPPED value of the Optional,
                // so its type and initializer must be the inner type's, not
                // Optional's.
                String innerType = optionalInnerType(method.returnType());

                testData.add(new TestData(
                        variableName,
                        innerType,
                        valueResolver.valueFor(innerType)
                ));
            }
        }

        for (AssignmentModel assignment : requiredAssignments(method)) {
            if (assignment.expression().isBlank()) {
                continue;
            }
            testData.add(new TestData(
                    assignment.variableName(),
                    assignment.variableType(),
                    createInitialization(assignment)
            ));
        }

        return testData;
    }

    private String parameterInitializerWithOverride(
            ParameterModel parameter,
            String fieldName,
            String overrideValue) {

        String type = parameter.type();

        if (isWellKnownImmutable(type)) {
            // Scalars can't have DTO fields; fall back to the normal path.
            return parameterInitializer(parameter);
        }

        return valueResolver.valueFor(
                type,
                java.util.Map.of(fieldName, overrideValue)
        );
    }

    /**
     * If the guard expression inspects a getter on one of the method's
     * parameters (e.g. request.getRecipient()), return the parameter name,
     * the field name, and a literal value that makes the guard fire.
     * <p>
     * Handles the common shapes:
     *   Utils.isBlank(param.getX())        -> ""
     *   param.getX().isBlank()             -> ""
     *   param.getX().isEmpty()             -> ""
     *   !param.getX().isEmpty()            -> ""
     *   param.getX() == null               -> "null"
     */
    private GuardedField guardedField(
            ConditionModel condition,
            MethodModel method) {

        if (condition == null) {
            return null;
        }

        String expr = condition.expression();

        for (ParameterModel parameter : method.parameters()) {

            Pattern p = Pattern.compile(
                    "\\b" + Pattern.quote(parameter.name())
                    + "\\s*\\.\\s*get([A-Z][A-Za-z0-9_]*)\\s*\\(\\)"
            );
            var m = p.matcher(expr);
            if (!m.find()) {
                continue;
            }

            String field = m.group(1);
            String fieldName = Character.toLowerCase(field.charAt(0))
                               + field.substring(1);

            // Choose the overriding literal:
            //   - "== null" on the getter -> null
            //   - anything blank/empty-ish -> ""
            boolean wantsNull = Pattern.compile(
                    "\\b" + Pattern.quote(parameter.name())
                    + "\\s*\\.\\s*get" + field + "\\s*\\(\\s*\\)"
                    + "\\s*==\\s*null\\b"
            ).matcher(expr).find();

            String value = wantsNull ? "null" : "\"\"";

            return new GuardedField(parameter.name(), fieldName, value);
        }

        return null;
    }

    /**
     * If the given type is Optional<T>, return T. Otherwise, return the
     * type unchanged. Used to describe the unwrapped value that orElseThrow
     * would produce.
     */
    private String optionalInnerType(String type) {
        if (type == null) return type;
        if (!type.startsWith("Optional<")) return type;

        int lt = type.indexOf('<');
        int gt = type.lastIndexOf('>');
        if (lt < 0 || gt < 0 || gt <= lt) return type;

        return type.substring(lt + 1, gt).trim();
    }

    private String guardTriggeringValue(
            ParameterModel parameter,
            ConditionModel condition) {

        String expr = condition.expression();
        String name = parameter.name();

        // If the guard specifically checks blank/empty, null triggers it too
        // (assuming the guard is `x == null || x.isBlank()`), so null is
        // always safe. But if we want a more targeted value, we can choose
        // "" for isBlank/isEmpty checks. Either works for `||` guards.
        boolean checksBlankish =
                Pattern.compile("\\b" + Pattern.quote(name)
                                + "\\s*\\.\\s*(isBlank|isEmpty)\\s*\\(")
                        .matcher(expr).find()
                || Pattern.compile("\\b(isBlank|isEmpty)\\s*\\(\\s*"
                                   + Pattern.quote(name) + "\\s*\\)")
                        .matcher(expr).find();

        if (checksBlankish) {
            // "" makes both isBlank and isEmpty true, and it also makes
            // `x == null` false — so only choose it when the guard uses `||`
            // with a null check, or when there is no null check at all.
            if (expr.contains(name + " == null")) {
                return "null";      // null satisfies both branches of `||`
            }
            return "\"\"";
        }

        return "null";
    }

    private String nullCheckedParameter(
            ConditionModel condition,
            MethodModel method) {

        if (condition == null) {
            return null;
        }

        String expr = condition.expression();

        for (ParameterModel parameter : method.parameters()) {
            if (parameterIsConstrained(expr, parameter.name())) {
                return parameter.name();
            }
        }
        return null;
    }

    /**
     * Does the guard expression constrain this parameter in a way that
     * a null / blank / empty value would trigger?
     */
    private boolean parameterIsConstrained(String expression, String name) {

        // status == null
        // status != null (negated form is also "constrained")
        // status.isBlank()
        // status.isEmpty()
        // !status.isEmpty()
        // NotificationUtils.isBlank(status)
        // Objects.isNull(status) / Objects.nonNull(status)

        // Direct comparisons.
        if (Pattern.compile("\\b" + Pattern.quote(name) + "\\s*==\\s*null\\b")
                .matcher(expression).find()) {
            return true;
        }
        if (Pattern.compile("\\b" + Pattern.quote(name) + "\\s*!=\\s*null\\b")
                .matcher(expression).find()) {
            return true;
        }

        // status.isBlank() / status.isEmpty() / status.isPresent() etc.
        if (Pattern.compile("\\b" + Pattern.quote(name) + "\\s*\\.\\s*(isBlank|isEmpty)\\s*\\(")
                .matcher(expression).find()) {
            return true;
        }

        // Wrapper forms: X.isBlank(status) / X.isEmpty(status) / Objects.isNull(status)
        if (Pattern.compile("\\b(isBlank|isEmpty|isNull|nonNull)\\s*\\(\\s*"
                            + Pattern.quote(name) + "\\s*\\)")
                .matcher(expression).find()) {
            return true;
        }

        return false;
    }

    private List<AssignmentModel> requiredAssignments(MethodModel method) {

        List<String> referenced = new ArrayList<>();

        method.returns().stream()
                .map(ReturnModel::expression)
                .forEach(referenced::add);

        method.methodCalls().stream()
                .flatMap(call -> call.arguments().stream())
                .forEach(referenced::add);

        method.throwsStatements().stream()
                .map(ThrowModel::expression)
                .forEach(referenced::add);

        method.conditions().stream()
                .map(ConditionModel::expression)
                .forEach(referenced::add);

        return method.assignments().stream()
                .filter(a -> !a.expression().isBlank())
                .filter(a ->
                        isPrimitiveType(a.variableType())
                        || isWellKnownImmutable(a.variableType())
                        || referenced.stream().anyMatch(expr ->
                                Pattern.compile("\\b" + Pattern.quote(a.variableName()) + "\\b")
                                        .matcher(expr).find()))
                .toList();
    }

    private boolean isPrimitiveType(String type) {
        return switch (type) {
            case "int", "long", "short", "byte",
                 "double", "float", "boolean", "char" -> true;
            default -> false;
        };
    }

    private String createInitialization(AssignmentModel assignment) {
        String expression = assignment.expression();
        String type = assignment.variableType();

        if (isWellKnownImmutable(type)) {
            return defaultValueFor(type);
        }

        // Real empty collections are almost always what you want as a
        // default in tests, and they compile.
        if (type.startsWith("List<") || type.startsWith("java.util.List<")) {
            return "java.util.List.of()";
        }
        if (type.startsWith("Set<") || type.startsWith("java.util.Set<")) {
            return "java.util.Set.of()";
        }
        if (type.startsWith("Map<") || type.startsWith("java.util.Map<")) {
            return "java.util.Map.of()";
        }

        if (expression.startsWith("new ")) {
            return expression;
        }

        if (expression.contains(".")) {
            return "mock(" + eraseGenerics(type) + ".class)";
        }

        return expression;
    }

    private String eraseGenerics(String type) {
        int idx = type.indexOf('<');
        return idx < 0 ? type : type.substring(0, idx);
    }

    private boolean isWellKnownImmutable(String type) {
        return switch (type) {
            case "String", "Long", "Integer", "int", "long",
                 "Double", "double", "Float", "float",
                 "Boolean", "boolean", "Short", "short",
                 "Byte", "byte", "Character", "char" -> true;
            default -> false;
        };
    }

    private String defaultValueFor(String type) {
        if (type != null && type.startsWith("Optional<")) {
            return "java.util.Optional.empty()";
        }

        return switch (type) {
            case "String" -> "\"test@example.com\"";
            case "Long", "long" -> "1L";
            case "Integer", "int" -> "1";
            case "Double", "double" -> "1.0";
            case "Float", "float" -> "1.0f";
            case "Boolean", "boolean" -> "true";
            case "Short", "short" -> "(short) 1";
            case "Byte", "byte" -> "(byte) 1";
            case "Character", "char" -> "'a'";
            default -> "null";
        };
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------
    private String parameterInitializer(ParameterModel parameter) {
        String type = parameter.type();

        if (isWellKnownImmutable(type)) {
            return defaultValueFor(type);
        }

        return dtoInitializer(type);
    }

    private String dtoInitializer(String type) {
        return valueResolver.valueFor(type);
    }

    private String simpleName(String type) {
        if (type == null || type.isBlank()) return type;
        int generic = type.indexOf('<');
        String noGenerics = generic < 0 ? type : type.substring(0, generic);
        int lastDot = noGenerics.lastIndexOf('.');
        return lastDot < 0 ? noGenerics : noGenerics.substring(lastDot + 1);
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private boolean isOptionalOrElseThrow(
            MethodModel method,
            MethodCallModel call) {

        if (call.kind() != CallKind.DEPENDENCY) {
            return false;
        }

        return method.returns().stream()
                .map(ReturnModel::expression)
                .anyMatch(e -> e.contains(
                        call.target() + "." + call.methodName())
                               && e.endsWith(".orElseThrow()"));
    }

    private String optionalExpectedVariable(
            MethodModel method,
            MethodCallModel call) {

        String returnType = method.returnType();
        if (returnType == null || returnType.isBlank()) {
            return "expectedValue";
        }

        String simple = simpleName(eraseGenerics(returnType));  // e.g. "Optional"
        return "expected"
               + Character.toUpperCase(simple.charAt(0))
               + simple.substring(1);
    }
}