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
import com.example.testgenerator.planning.model.ExpectedOutcome;
import com.example.testgenerator.planning.model.MockAction;
import com.example.testgenerator.planning.model.MockSetup;
import com.example.testgenerator.planning.model.OutcomeKind;
import com.example.testgenerator.planning.model.TestData;
import com.example.testgenerator.planning.model.TestScenario;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class DefaultTestPlanner implements TestPlanner {

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

        // Stub every dependency call in the condition so the guard trips.
        String stubValue = stubValueForCondition(condition.expression());

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
                    stubValue
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

        // 1. Neutralize every guard clause that sits above the try, so
        //    execution can actually reach the try body.
        setups.addAll(guardNeutralizingSetups(method));

        // 2. Pick the first dependency call in the try body as the one that
        //    throws the caught exception.
        MethodCallModel throwingCall = firstDependencyCall(tryModel.bodyCalls());

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

        // 3. Verify the dependency calls in the catch body.
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

            // Guard must be above the try — tryDepth 0 and not in a catch.
            // Otherwise it belongs to a different scenario shape.
            if (condition.context() != null) {
                if (condition.context().tryDepth() > 0
                    || condition.context().insideCatch()) {
                    continue;
                }
            }

            String stub = negateConditionStub(
                    stubValueForCondition(condition.expression()));

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
                        stub
                ));
            }
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

        Set<String> guardCallKeys = method.conditions().stream()
                .filter(this::isGuardClause)
                .flatMap(c -> c.methodCalls().stream())
                .map(this::callKey)
                .collect(Collectors.toSet());

        for (MethodCallModel call : method.methodCalls()) {

            // Skip catch-body calls; they're exercised by catch scenarios.
            if (call.context() != null && call.context().insideCatch()) {
                continue;
            }

            // Skip guard-clause predicate calls; they're exercised by
            // guard scenarios. On the happy path, they evaluate to the
            // opposite value, but since the test doesn't currently stub
            // them, Mockito's default (false) is what trips them off.
            // We still stub them explicitly to be deterministic.
            if (guardCallKeys.contains(callKey(call)) && call.kind() == CallKind.DEPENDENCY) {
                setups.add(new MockSetup(
                        call.target(),
                        call.targetType(),
                        call.methodName(),
                        normalizeArguments(call.arguments(), method),
                        MockAction.RETURN,
                        negateConditionStub(
                                stubValueForCondition(
                                        conditionFor(method, call)
                                )
                        )
                ));
                continue;
            }

            if (call.kind() != CallKind.DEPENDENCY) {
                continue;
            }

            setups.add(createMockSetup(call, method));
        }

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

    private MockSetup createMockSetup(
            MethodCallModel call,
            MethodModel method) {

        if (isOptionalOrElseThrow(method, call)) {
            return new MockSetup(
                    call.target(),
                    call.targetType(),
                    call.methodName(),
                    normalizeArguments(call.arguments(), method),
                    MockAction.RETURN,
                    "Optional.of("
                    + optionalExpectedVariable(method, call)
                    + ")"
            );
        }

        String value = findReturnValue(call, method);

        if (!"null".equals(value)) {
            return new MockSetup(
                    call.target(),
                    call.targetType(),
                    call.methodName(),
                    normalizeArguments(call.arguments(), method),
                    MockAction.RETURN,
                    value
            );
        }

        // No assignment captures the result, and the call is not a chain
        // returning Optional -> treat it as a side-effecting call we
        // verify rather than stub.
        return new MockSetup(
                call.target(),
                call.targetType(),
                call.methodName(),
                normalizeArguments(call.arguments(), method),
                MockAction.VERIFY,
                ""
        );
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

        for (MethodCallModel call : method.methodCalls()) {
            if ("void".equals(method.returnType())) {
                return new ExpectedOutcome(OutcomeKind.VOID, "");
            }
            if (isOptionalOrElseThrow(method, call)) {
                return new ExpectedOutcome(
                        OutcomeKind.RETURN_VALUE,
                        optionalExpectedVariable(method, call)
                );
            }
        }

        if (!method.returns().isEmpty()) {
            ReturnModel returnModel = method.returns().getLast();
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

        for (ParameterModel parameter : method.parameters()) {
            String initialization;

            if (parameter.name().equals(nullCheckedParam)) {
                initialization = "null";
            } else {
                initialization = defaultValueFor(parameter.type(), condition);
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
                testData.add(new TestData(
                        variableName,
                        method.returnType(),
                        "mock(" + method.returnType() + ".class)"
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

    private String nullCheckedParameter(
            ConditionModel condition,
            MethodModel method) {

        if (condition == null) {
            return null;
        }

        for (ParameterModel parameter : method.parameters()) {
            // Look for "<param> == null" in the condition text.
            String trimmed = condition.expression().trim();
            if (trimmed.equals(parameter.name() + " == null")) {
                return parameter.name();
            }
        }
        return null;
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
                .filter(a -> referenced.stream().anyMatch(expr ->
                        Pattern.compile("\\b" + Pattern.quote(a.variableName()) + "\\b")
                                .matcher(expr).find()))
                .filter(a -> !a.expression().isBlank())
                .toList();
    }

    private String createInitialization(AssignmentModel assignment) {
        String expression = assignment.expression();
        String type = assignment.variableType();

        if (isWellKnownImmutable(type)) {
            return defaultValueFor(type, null);
        }
        if (expression.startsWith("new ")) {
            return expression;
        }
        if (expression.contains(".")) {
            return "mock(" + type + ".class)";
        }
        return expression;
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

    private String defaultValueFor(
            String type,
            ConditionModel condition) {

        // If the condition checks this parameter against null, give it
        // a non-null default so the happy path is reachable.
        // (Reverse for guard scenarios is handled elsewhere.)
        return switch (type) {

            case "String" -> "\"test@example.com\"";
            case "Long" -> "1L";
            case "Integer", "int" -> "1";
            case "long" -> "1L";
            case "Double", "double" -> "1.0";
            case "Float", "float" -> "1.0f";
            case "Boolean", "boolean" -> "true";
            case "Short", "short" -> "(short) 1";
            case "Byte", "byte" -> "(byte) 1";
            case "Character", "char" -> "'a'";
            default -> "mock(" + type + ".class)";
        };
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

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
        return "expected"
               + Character.toUpperCase(returnType.charAt(0))
               + returnType.substring(1);
    }
}