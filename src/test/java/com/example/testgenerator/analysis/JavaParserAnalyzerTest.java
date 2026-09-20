package com.example.testgenerator.analysis;

import com.example.testgenerator.analysis.model.CallKind;
import com.example.testgenerator.analysis.model.ClassModel;
import com.example.testgenerator.analysis.model.ConditionModel;
import com.example.testgenerator.analysis.model.DependencyKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class JavaParserAnalyzerTest {

    private JavaParserAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        StatementContextResolver contextResolver = new StatementContextResolver();
        JavaParserMethodCallAnalyzer methodCallAnalyzer = new JavaParserMethodCallAnalyzer(contextResolver);
        analyzer = new JavaParserAnalyzer(
                new SpringTypeClassifier(),
                new ConstructorResolver(),
                methodCallAnalyzer,
                new JavaParserConditionAnalyzer(methodCallAnalyzer, contextResolver)
        );
    }

    @Test
    void shouldAnalyzeSpringService() {

        // Given
        Path sourceFile = Path.of(
                "src/test/resources/fixtures/UserService.java"
        );

        // When
        ClassModel result = analyzer.analyze(sourceFile);

        // Then
        assertThat(result.packageName())
                .isEqualTo("fixtures");

        assertThat(result.className())
                .isEqualTo("UserService");

        assertThat(result.annotations())
                .containsExactly("Service", "RequiredArgsConstructor");

        assertThat(result.dependencies())
                .containsExactly(
                        new com.example.testgenerator.analysis.model.DependencyModel(
                                "UserRepository",
                                "userRepository",
                                DependencyKind.MOCK
                        ),
                        new com.example.testgenerator.analysis.model.DependencyModel(
                                "EmailService",
                                "emailService",
                                DependencyKind.MOCK
                        )
                );

        assertThat(result.methods())
                .extracting("name")
                .containsExactly(
                        "findById",
                        "create"
                );
    }

    @Test
    void shouldAnalyzeMethodCalls() {

        // Given
        Path sourceFile = Path.of(
                "src/test/resources/fixtures/UserService.java"
        );

        // When
        ClassModel result =
                analyzer.analyze(sourceFile);

        var findById =
                result.methods()
                        .stream()
                        .filter(method ->
                                method.name().equals("findById"))
                        .findFirst()
                        .orElseThrow();

        // Then
        assertThat(findById.methodCalls())
                .anySatisfy(call -> {
                    assertThat(call.target())
                            .isEqualTo("userRepository");

                    assertThat(call.methodName())
                            .isEqualTo("findById");

                    assertThat(call.arguments())
                            .containsExactly("id");

                    assertThat(call.kind())
                            .isEqualTo(CallKind.DEPENDENCY);
                });
    }

    @Test
    void shouldAnalyzeConditions() {

        Path sourceFile = Path.of(
                "src/test/resources/fixtures/UserService.java"
        );

        ClassModel result =
                analyzer.analyze(sourceFile);

        var createMethod =
                result.methods()
                        .stream()
                        .filter(method ->
                                method.name().equals("create"))
                        .findFirst()
                        .orElseThrow();

        assertThat(createMethod.conditions())
                .hasSize(1);

        ConditionModel condition =
                createMethod.conditions().getFirst();

        assertThat(condition.expression())
                .isEqualTo(
                        "userRepository.existsByEmail(email)"
                );

        assertThat(condition.thrownExceptions())
                .anySatisfy(exception ->
                        assertThat(exception)
                                .contains("DuplicateUserException"));
    }

    @Test
    void shouldAnalyzeReturnsAssignmentsAndThrows() {

        Path sourceFile = Path.of(
                "src/test/resources/fixtures/UserService.java"
        );

        ClassModel result =
                analyzer.analyze(sourceFile);

        var createMethod =
                result.methods()
                        .stream()
                        .filter(method ->
                                method.name().equals("create"))
                        .findFirst()
                        .orElseThrow();

        assertThat(createMethod.returns())
                .anySatisfy(returnModel ->
                        assertThat(returnModel.expression())
                                .isEqualTo("savedUser")
                );

        assertThat(createMethod.assignments())
                .anySatisfy(assignment -> {
                    assertThat(assignment.variableName())
                            .isEqualTo("savedUser");

                    assertThat(assignment.variableType())
                            .isEqualTo("User");

                    assertThat(assignment.expression())
                            .isEqualTo(
                                    "userRepository.save(user)"
                            );
                });

        assertThat(createMethod.assignments())
                .anySatisfy(assignment -> {
                    assertThat(assignment.variableName())
                            .isEqualTo("user");

                    assertThat(assignment.variableType())
                            .isEqualTo("User");

                    assertThat(assignment.expression())
                            .isEqualTo("new User(email)");
                });

        assertThat(createMethod.throwsStatements())
                .anySatisfy(throwModel -> {
                    assertThat(throwModel.exceptionType())
                            .isEqualTo("DuplicateUserException");
                });
    }


}