//package com.example.testgenerator.planning;
//
//import com.example.testgenerator.analysis.model.*;
//import com.example.testgenerator.planning.model.*;
//import org.apache.catalina.User;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//
//import java.util.List;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.mockito.Mockito.*;
//
//class DefaultTestPlannerTest {
//
//    private TestPlanner planner;
//
//    @BeforeEach
//    void setUp() {
//        planner = new DefaultTestPlanner();
//    }
//
//    @Test
//    void shouldCreateBasicScenarioForMethodWithoutConditions() {
//
//        MethodModel method = new MethodModel(
//                "findById",
//                "User",
//                List.of(
//                        new ParameterModel(
//                                "Long",
//                                "id"
//                        )
//                ),
//                List.of(
//                        new MethodCallModel(
//                                "userRepository",
//                                "findById",
//                                List.of("id"),
//                                CallKind.DEPENDENCY
//                        )
//                ),
//                List.of(),
//                List.of(
//                        new ReturnModel("user")
//                ),
//                List.of(
//                        new AssignmentModel(
//                                "user",
//                                "User",
//                                "userRepository.findById(id)"
//                        )
//                ),
//                List.of()
//        );
//
//        ClassModel classModel = new ClassModel(
//                "com.example.users",
//                "UserService",
//                List.of("Service"),
//                List.of(
//                        new DependencyModel(
//                                "UserRepository",
//                                "userRepository",
//                                DependencyKind.MOCK
//                        )
//                ),
//                List.of(method),
//                SpringType.SERVICE
//        );
//
//        List<TestScenario> scenarios =
//                planner.plan(classModel);
//
//        assertThat(scenarios)
//                .hasSize(1);
//
//        TestScenario scenario =
//                scenarios.getFirst();
//
//        assertThat(scenario.methodName())
//                .isEqualTo("findById");
//
//        assertThat(scenario.displayName())
//                .isEqualTo(
//                        "findById should execute successfully"
//                );
//
//        assertThat(scenario.returnType())
//                .isEqualTo("User");
//
//        assertThat(scenario.parameters())
//                .containsExactly(
//                        new ParameterModel(
//                                "Long",
//                                "id"
//                        )
//                );
//
//        assertThat(scenario.testData())
//                .containsExactly(
//                        new TestData(
//                                "id",
//                                "Long",
//                                "1L"
//                        )
//                );
//
//        assertThat(scenario.mockSetups())
//                .hasSize(1);
//
//        MockSetup setup =
//                scenario.mockSetups().getFirst();
//
//        assertThat(setup.dependency())
//                .isEqualTo("userRepository");
//
//        assertThat(setup.method())
//                .isEqualTo("findById");
//
//        assertThat(setup.arguments())
//                .containsExactly("id");
//
//        assertThat(setup.action())
//                .isEqualTo(MockAction.RETURN);
//
//        assertThat(setup.value())
//                .isEqualTo("userRepository.findById(id)");
//
//        assertThat(scenario.expectedOutcome())
//                .isEqualTo(
//                        new ExpectedOutcome(
//                                OutcomeKind.RETURN_VALUE,
//                                "userRepository.findById(id)"
//                        )
//                );
//    }
//
//    @Test
//    void shouldCreateExceptionAndSuccessScenariosForCondition() {
//
//        MethodModel method = new MethodModel(
//                "create",
//                "User",
//                List.of(
//                        new ParameterModel(
//                                "String",
//                                "email"
//                        )
//                ),
//                List.of(
//                        new MethodCallModel(
//                                "userRepository",
//                                "existsByEmail",
//                                List.of("email"),
//                                CallKind.DEPENDENCY
//                        )
//                ),
//                List.of(
//                        new ConditionModel(
//                                "userRepository.existsByEmail(email)",
//                                List.of(
//                                        new MethodCallModel(
//                                                "userRepository",
//                                                "existsByEmail",
//                                                List.of("email"),
//                                                null
//                                        )
//                                ),
//                                List.of(
//                                        "DuplicateUserException"
//                                )
//                        )
//                ),
//                List.of(
//                        new ReturnModel("savedUser")
//                ),
//                List.of(
//                        new AssignmentModel(
//                                "user",
//                                "User",
//                                "new User(email)"
//                        ),
//                        new AssignmentModel(
//                                "savedUser",
//                                "User",
//                                "userRepository.save(user)"
//                        )
//                ),
//                List.of(
//                        new ThrowModel(
//                                "DuplicateUserException",
//                                "new DuplicateUserException()"
//                        )
//                )
//        );
//
//        ClassModel classModel = new ClassModel(
//                "com.example.users",
//                "UserService",
//                List.of("Service"),
//                List.of(
//                        new DependencyModel(
//                                "UserRepository",
//                                "userRepository",
//                                DependencyKind.MOCK
//                        ),
//                        new DependencyModel(
//                                "EmailService",
//                                "emailService",
//                                DependencyKind.MOCK
//                        )
//                ),
//                List.of(method),
//                SpringType.SERVICE
//        );
//
//        List<TestScenario> scenarios =
//                planner.plan(classModel);
//
//        assertThat(scenarios)
//                .hasSize(2);
//
//        TestScenario exceptionScenario =
//                scenarios.get(0);
//
//        assertThat(exceptionScenario.displayName())
//                .isEqualTo(
//                        "create should throw DuplicateUserException"
//                );
//
//        assertThat(exceptionScenario.mockSetups())
//                .hasSize(1);
//
//        MockSetup exceptionSetup =
//                exceptionScenario.mockSetups().getFirst();
//
//        assertThat(exceptionSetup.dependency())
//                .isEqualTo("userRepository");
//
//        assertThat(exceptionSetup.method())
//                .isEqualTo("existsByEmail");
//
//        assertThat(exceptionSetup.arguments())
//                .containsExactly("email");
//
//        assertThat(exceptionSetup.action())
//                .isEqualTo(MockAction.RETURN);
//
//        assertThat(exceptionSetup.value())
//                .isEqualTo("true");
//
//        assertThat(exceptionScenario.expectedOutcome())
//                .isEqualTo(
//                        new ExpectedOutcome(
//                                OutcomeKind.THROW_EXCEPTION,
//                                "DuplicateUserException"
//                        )
//                );
//
//        TestScenario successScenario =
//                scenarios.get(1);
//
//        assertThat(successScenario.displayName())
//                .isEqualTo(
//                        "create should execute successfully"
//                );
//
//        assertThat(successScenario.mockSetups())
//                .hasSize(3);
//
//        assertThat(successScenario.mockSetups())
//                .anySatisfy(setup -> {
//                    assertThat(setup.dependency())
//                            .isEqualTo("userRepository");
//
//                    assertThat(setup.method())
//                            .isEqualTo("existsByEmail");
//
//                    assertThat(setup.action())
//                            .isEqualTo(MockAction.RETURN);
//
//                    assertThat(setup.value())
//                            .isEqualTo("false");
//                });
//
//        assertThat(successScenario.mockSetups())
//                .anySatisfy(setup -> {
//                    assertThat(setup.dependency())
//                            .isEqualTo("userRepository");
//
//                    assertThat(setup.method())
//                            .isEqualTo("save");
//
//                    assertThat(setup.action())
//                            .isEqualTo(MockAction.RETURN);
//
//                    assertThat(setup.value())
//                            .isEqualTo("savedUser");
//                });
//
//        assertThat(successScenario.mockSetups())
//                .anySatisfy(setup -> {
//                    assertThat(setup.dependency())
//                            .isEqualTo("userRepository");
//
//                    assertThat(setup.method())
//                            .isEqualTo("existsByEmail");
//
//                    assertThat(setup.action())
//                            .isEqualTo(MockAction.RETURN);
//
//                    assertThat(setup.value())
//                            .isEqualTo("false");
//                });
//
//        assertThat(successScenario.expectedOutcome())
//                .isEqualTo(
//                        new ExpectedOutcome(
//                                OutcomeKind.RETURN_VALUE,
//                                "savedUser"
//                        )
//                );
//    }
//
//    @Test
//    void shouldCreateDefaultTestDataForMethodParameters() {
//
//        MethodModel method = new MethodModel(
//                "testMethod",
//                "void",
//                List.of(
//                        new ParameterModel(
//                                "String",
//                                "name"
//                        ),
//                        new ParameterModel(
//                                "Long",
//                                "id"
//                        ),
//                        new ParameterModel(
//                                "int",
//                                "count"
//                        ),
//                        new ParameterModel(
//                                "boolean",
//                                "active"
//                        )
//                ),
//                List.of(),
//                List.of(),
//                List.of(),
//                List.of(),
//                List.of()
//        );
//
//        ClassModel classModel = new ClassModel(
//                "com.example",
//                "ExampleService",
//                List.of("Service"),
//                List.of(),
//                List.of(method),
//                SpringType.SERVICE
//        );
//
//        List<TestScenario> scenarios =
//                planner.plan(classModel);
//
//        assertThat(scenarios)
//                .hasSize(1);
//
//        TestScenario scenario =
//                scenarios.getFirst();
//
//        assertThat(scenario.testData())
//                .containsExactly(
//                        new TestData(
//                                "name",
//                                "String",
//                                "\"test@example.com\""
//                        ),
//                        new TestData(
//                                "id",
//                                "Long",
//                                "1L"
//                        ),
//                        new TestData(
//                                "count",
//                                "int",
//                                "1"
//                        ),
//                        new TestData(
//                                "active",
//                                "boolean",
//                                "true"
//                        )
//                );
//    }
//
//    @Test
//    void shouldCreateVoidOutcomeWhenMethodHasNoReturnOrThrow() {
//
//        MethodModel method = new MethodModel(
//                "sendEmail",
//                "void",
//                List.of(
//                        new ParameterModel(
//                                "String",
//                                "email"
//                        )
//                ),
//                List.of(
//                        new MethodCallModel(
//                                "emailClient",
//                                "send",
//                                List.of("email"),
//                                CallKind.DEPENDENCY
//                        )
//                ),
//                List.of(),
//                List.of(),
//                List.of(),
//                List.of()
//        );
//
//        ClassModel classModel = new ClassModel(
//                "com.example",
//                "EmailService",
//                List.of("Service"),
//                List.of(
//                        new DependencyModel(
//                                "EmailClient",
//                                "emailClient",
//                                DependencyKind.MOCK
//                        )
//                ),
//                List.of(method),
//                SpringType.SERVICE
//        );
//
//        List<TestScenario> scenarios =
//                planner.plan(classModel);
//
//        assertThat(scenarios)
//                .hasSize(1);
//
//        TestScenario scenario =
//                scenarios.getFirst();
//
//        assertThat(scenario.expectedOutcome())
//                .isEqualTo(
//                        new ExpectedOutcome(
//                                OutcomeKind.VOID,
//                                null
//                        )
//                );
//
//        assertThat(scenario.mockSetups())
//                .hasSize(1);
//
//        assertThat(scenario.mockSetups().getFirst().action())
//                .isEqualTo(MockAction.VERIFY);
//    }
//}