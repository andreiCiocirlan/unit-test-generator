package com.example.testgenerator.planning;

import com.example.testgenerator.analysis.model.AssignmentModel;
import com.example.testgenerator.analysis.model.CallKind;
import com.example.testgenerator.analysis.model.ClassModel;
import com.example.testgenerator.analysis.model.ConditionModel;
import com.example.testgenerator.analysis.model.MethodCallModel;
import com.example.testgenerator.analysis.model.MethodModel;
import com.example.testgenerator.analysis.model.ReturnModel;
import com.example.testgenerator.analysis.model.ThrowModel;
import com.example.testgenerator.planning.model.ExpectedOutcome;
import com.example.testgenerator.planning.model.MockAction;
import com.example.testgenerator.planning.model.MockSetup;
import com.example.testgenerator.planning.model.OutcomeKind;
import com.example.testgenerator.planning.model.TestData;
import com.example.testgenerator.planning.model.TestScenario;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DefaultTestPlanner implements TestPlanner {

    @Override
    public List<TestScenario> plan(ClassModel classModel) {

        List<TestScenario> scenarios =
                new ArrayList<>();

        for (MethodModel method : classModel.methods()) {

            if (method.conditions().isEmpty()) {

                scenarios.add(
                        createBasicScenario(method)
                );

                continue;
            }

            for (ConditionModel condition :
                    method.conditions()) {

                scenarios.add(
                        createExceptionScenario(
                                method,
                                condition
                        )
                );

                scenarios.add(
                        createSuccessScenario(
                                method,
                                condition
                        )
                );
            }
        }

        return scenarios;
    }

    private TestScenario createBasicScenario(
            MethodModel method) {

        return new TestScenario(
                method.name(),
                method.name()
                        + " should execute successfully",
                method.returnType(),
                method.parameters(),
                createTestData(method),
                dependencyCalls(method),
                createNormalExpectedOutcome(method)
        );
    }

    private TestScenario createExceptionScenario(
            MethodModel method,
            ConditionModel condition) {

        List<MockSetup> mockSetups =
                new ArrayList<>();

        /*
         * Make the condition true.
         */
        for (MethodCallModel call :
                condition.methodCalls()) {

            mockSetups.add(
                    new MockSetup(
                            call.target(),
                            call.methodName(),
                            call.arguments(),
                            MockAction.RETURN,
                            "true"
                    )
            );
        }

        String exceptionType =
                condition.thrownExceptions()
                        .stream()
                        .findFirst()
                        .orElse("RuntimeException");

        return new TestScenario(
                method.name(),
                method.name()
                        + " should throw "
                        + exceptionType,
                method.returnType(),
                method.parameters(),
                createTestData(method),
                mockSetups,
                new ExpectedOutcome(
                        OutcomeKind.THROW_EXCEPTION,
                        exceptionType
                )
        );
    }

    private TestScenario createSuccessScenario(
            MethodModel method,
            ConditionModel condition) {

        List<MockSetup> mockSetups =
                new ArrayList<>();

        for (MethodCallModel call :
                method.methodCalls()) {

            /*
             * The condition itself must evaluate to false.
             */
            if (belongsToCondition(
                    call,
                    condition)) {

                mockSetups.add(
                        new MockSetup(
                                call.target(),
                                call.methodName(),
                                call.arguments(),
                                MockAction.RETURN,
                                "false"
                        )
                );

                continue;
            }

            if (call.kind()
                    == CallKind.DEPENDENCY) {

                mockSetups.add(
                        createMockSetup(
                                call,
                                method
                        )
                );
            }
        }

        return new TestScenario(
                method.name(),
                method.name()
                        + " should execute successfully",
                method.returnType(),
                method.parameters(),
                createTestData(method),
                mockSetups,
                createNormalExpectedOutcome(method)
        );
    }

    private boolean belongsToCondition(
            MethodCallModel call,
            ConditionModel condition) {

        return condition.methodCalls()
                .stream()
                .anyMatch(conditionCall ->
                        conditionCall.target()
                                .equals(call.target())
                                && conditionCall.methodName()
                                .equals(call.methodName())
                                && conditionCall.arguments()
                                .equals(call.arguments())
                );
    }

    private List<MockSetup> dependencyCalls(
            MethodModel method) {

        return method.methodCalls()
                .stream()
                .filter(call ->
                        call.kind()
                                == CallKind.DEPENDENCY)
                .map(call ->
                        createMockSetup(
                                call,
                                method
                        )
                )
                .toList();
    }

    private MockSetup createMockSetup(
            MethodCallModel call,
            MethodModel method) {

        String value =
                findReturnValue(
                        call,
                        method
                );

        /*
         * For now, treat calls with a known return
         * assignment as RETURN.
         *
         * Void methods are handled as VERIFY.
         */
        if (!value.equals("null")) {

            return new MockSetup(
                    call.target(),
                    call.methodName(),
                    normalizeArguments(
                            call.arguments(),
                            method
                    ),
                    MockAction.RETURN,
                    value
            );
        }

        return new MockSetup(
                call.target(),
                call.methodName(),
                normalizeArguments(
                        call.arguments(),
                        method
                ),
                MockAction.VERIFY,
                ""
        );
    }

    private List<String> normalizeArguments(
            List<String> arguments,
            MethodModel method) {

        return arguments.stream()
                .map(argument ->
                        normalizeMockArgument(
                                argument,
                                method
                        )
                )
                .toList();
    }

    private String normalizeMockArgument(
            String argument,
            MethodModel method) {

        return method.assignments()
                .stream()
                .filter(assignment ->
                        assignment.variableName()
                                .equals(argument))
                .filter(assignment ->
                        assignment.expression()
                                .startsWith("new "))
                .map(assignment ->
                        "any("
                                + assignment.variableType()
                                + ".class)"
                )
                .findFirst()
                .orElse(argument);
    }

    private String findReturnValue(
            MethodCallModel call,
            MethodModel method) {

        return method.assignments()
                .stream()
                .filter(assignment ->
                        assignment.expression()
                                .contains(
                                        call.target()
                                                + "."
                                                + call.methodName()
                                ))
                .map(AssignmentModel::variableName)
                .findFirst()
                .orElse("null");
    }

    private ExpectedOutcome createNormalExpectedOutcome(
            MethodModel method) {

        /*
         * Do not use method.throwsStatements() here.
         *
         * A throw inside a condition belongs to the
         * exception scenario for that condition. It does
         * not mean every execution of the method throws.
         */

        if (!method.returns().isEmpty()) {

            ReturnModel returnModel =
                    method.returns()
                            .getLast();

            return new ExpectedOutcome(
                    OutcomeKind.RETURN_VALUE,
                    returnModel.expression()
            );
        }

        return new ExpectedOutcome(
                OutcomeKind.VOID,
                ""
        );
    }

    private List<TestData> createTestData(
            MethodModel method) {

        List<TestData> testData =
                new ArrayList<>();

        testData.addAll(
                method.parameters()
                        .stream()
                        .map(parameter ->
                                new TestData(
                                        parameter.name(),
                                        parameter.type(),
                                        defaultValueFor(
                                                parameter.type()
                                        )
                                )
                        )
                        .toList()
        );

        for (AssignmentModel assignment :
                requiredAssignments(method)) {

            if (assignment.expression()
                    .isBlank()) {

                continue;
            }

            String initialization =
                    createInitialization(
                            assignment
                    );

            testData.add(
                    new TestData(
                            assignment.variableName(),
                            assignment.variableType(),
                            initialization
                    )
            );
        }

        return testData;
    }

    private List<AssignmentModel> requiredAssignments(
            MethodModel method) {

        List<String> requiredVariables =
                new ArrayList<>();

        method.returns()
                .stream()
                .map(ReturnModel::expression)
                .forEach(requiredVariables::add);

        method.methodCalls()
                .stream()
                .flatMap(call ->
                        call.arguments().stream())
                .forEach(requiredVariables::add);

        return method.assignments()
                .stream()
                .filter(assignment ->
                        requiredVariables.contains(
                                assignment.variableName()
                        )
                        || assignment.expression()
                                .startsWith("new "))
                .toList();
    }

    private String createInitialization(
            AssignmentModel assignment) {

        String expression =
                assignment.expression();

        if (expression.startsWith("new ")) {
            return expression;
        }

        if (expression.contains(".")) {
            return "mock("
                    + assignment.variableType()
                    + ".class)";
        }

        return expression;
    }

    private String defaultValueFor(
            String type) {

        return switch (type) {

            case "String" ->
                    "\"test@example.com\"";

            case "Long" ->
                    "1L";

            case "Integer", "int" ->
                    "1";

            case "long" ->
                    "1L";

            case "Double", "double" ->
                    "1.0";

            case "Float", "float" ->
                    "1.0f";

            case "Boolean", "boolean" ->
                    "true";

            case "Short", "short" ->
                    "(short) 1";

            case "Byte", "byte" ->
                    "(byte) 1";

            case "Character", "char" ->
                    "'a'";

            default ->
                    "null";
        };
    }
}