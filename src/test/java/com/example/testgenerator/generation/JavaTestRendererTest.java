package com.example.testgenerator.generation;

import com.example.testgenerator.analysis.*;
import com.example.testgenerator.analysis.model.ClassModel;
import com.example.testgenerator.analysis.model.DependencyKind;
import com.example.testgenerator.analysis.model.DependencyModel;
import com.example.testgenerator.analysis.model.SpringType;
import com.example.testgenerator.generation.model.TestRenderModel;
import com.example.testgenerator.planning.DefaultTestPlanner;
import com.example.testgenerator.planning.TestPlanner;
import com.example.testgenerator.planning.model.ExpectedOutcome;
import com.example.testgenerator.planning.model.OutcomeKind;
import com.example.testgenerator.planning.model.TestData;
import com.example.testgenerator.planning.model.TestScenario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JavaTestRendererTest {

    private JavaParserAnalyzer analyzer;
    private TestPlanner testPlanner;
    private JavaTestRenderer renderer;

    @BeforeEach
    void setUp() {
        analyzer = new JavaParserAnalyzer(
                new SpringTypeClassifier(),
                new ConstructorResolver(),
                new JavaParserMethodCallAnalyzer(),
                new JavaParserConditionAnalyzer(new JavaParserMethodCallAnalyzer())
        );

        testPlanner =
                new DefaultTestPlanner();

        renderer =
                new JavaTestRenderer();
    }

    @Test
    void shouldRenderDuplicateUserScenario() {

        Path sourceFile = Path.of(
                "src/test/resources/fixtures/UserService.java"
        );

        ClassModel classModel =
                analyzer.analyze(sourceFile);

        List<TestScenario> scenarios =
                testPlanner.plan(classModel);

        TestRenderModel renderModel =
                new TestRenderModel(
                        classModel.packageName(),
                        classModel.className() + "Test",
                        classModel.className(),
                        classModel.dependencies(),
                        scenarios
                );

        String source =
                renderer.render(renderModel);

        assertThat(source)
                .contains(
                        "@ExtendWith(MockitoExtension.class)"
                );

        assertThat(source)
                .contains("@Mock");

        assertThat(source)
                .contains(
                        "UserRepository userRepository"
                );

        assertThat(source)
                .contains(
                        "EmailService emailService"
                );

        assertThat(source)
                .contains("@InjectMocks");

        assertThat(source)
                .contains(
                        "UserService userService"
                );

        assertThat(source)
                .contains(
                        "when(userRepository.existsByEmail(email))"
                );

        assertThat(source)
                .contains(
                        ".thenReturn(true)"
                );

        assertThat(source)
                .contains(
                        "assertThatThrownBy"
                );

        assertThat(source)
                .contains(
                        "DuplicateUserException.class"
                );
    }

    @Test
    void shouldRenderMockitoMocks() {

        Path sourceFile = Path.of(
                "src/test/resources/fixtures/UserService.java"
        );

        ClassModel classModel =
                analyzer.analyze(sourceFile);

        List<TestScenario> scenarios =
                testPlanner.plan(classModel);

        TestRenderModel renderModel =
                new TestRenderModel(
                        classModel.packageName(),
                        classModel.className() + "Test",
                        classModel.className(),
                        classModel.dependencies(),
                        scenarios
                );

        String source =
                renderer.render(renderModel);

        assertThat(source)
                .contains(
                        "@Mock\n"
                                + "    private UserRepository "
                                + "userRepository;"
                );

        assertThat(source)
                .contains(
                        "@Mock\n"
                                + "    private EmailService "
                                + "emailService;"
                );

        assertThat(source)
                .contains(
                        "@InjectMocks\n"
                                + "    private UserService "
                                + "userService;"
                );
    }

    @Test
    void shouldRenderExceptionScenario() {

        Path sourceFile = Path.of(
                "src/test/resources/fixtures/UserService.java"
        );

        ClassModel classModel =
                analyzer.analyze(sourceFile);

        List<TestScenario> scenarios =
                testPlanner.plan(classModel);

        TestRenderModel renderModel =
                new TestRenderModel(
                        classModel.packageName(),
                        classModel.className() + "Test",
                        classModel.className(),
                        classModel.dependencies(),
                        scenarios
                );

        String source =
                renderer.render(renderModel);

        assertThat(source)
                .contains(
                        "when(userRepository.existsByEmail(email))"
                );

        assertThat(source)
                .contains(
                        ".thenReturn(true);"
                );

        assertThat(source)
                .contains(
                        "// When / Then"
                );

        assertThat(source)
                .contains(
                        "assertThatThrownBy(() -> userService.create(email))"
                );

        assertThat(source)
                .contains(
                        ".isInstanceOf("
                                + "DuplicateUserException.class);"
                );
    }

    @Test
    void shouldRenderMethodArguments() {

        Path sourceFile = Path.of(
                "src/test/resources/fixtures/UserService.java"
        );

        ClassModel classModel =
                analyzer.analyze(sourceFile);

        List<TestScenario> scenarios =
                testPlanner.plan(classModel);

        TestRenderModel renderModel =
                new TestRenderModel(
                        classModel.packageName(),
                        classModel.className() + "Test",
                        classModel.className(),
                        classModel.dependencies(),
                        scenarios
                );

        String source =
                renderer.render(renderModel);

        assertThat(source)
                .contains(
                        "userService.create(email)"
                );
    }

    @Test
    void shouldRenderJUnitAndMockitoImports() {

        Path sourceFile = Path.of(
                "src/test/resources/fixtures/UserService.java"
        );

        ClassModel classModel =
                analyzer.analyze(sourceFile);

        List<TestScenario> scenarios =
                testPlanner.plan(classModel);

        TestRenderModel renderModel =
                new TestRenderModel(
                        classModel.packageName(),
                        classModel.className() + "Test",
                        classModel.className(),
                        classModel.dependencies(),
                        scenarios
                );

        String source =
                renderer.render(renderModel);

        assertThat(source)
                .contains(
                        "import org.junit.jupiter.api.Test;"
                );

        assertThat(source)
                .contains(
                        "import org.junit.jupiter.api.extension."
                                + "ExtendWith;"
                );

        assertThat(source)
                .contains(
                        "import org.mockito.InjectMocks;"
                );

        assertThat(source)
                .contains(
                        "import org.mockito.Mock;"
                );

        assertThat(source)
                .contains(
                        "import org.mockito.junit.jupiter."
                                + "MockitoExtension;"
                );

        assertThat(source)
                .contains(
                        "import static org.mockito.Mockito.when;"
                );

        assertThat(source)
                .contains(
                        "import static org.mockito.Mockito.verify;"
                );

        assertThat(source)
                .contains(
                        "import static org.assertj.core.api.Assertions."
                                + "assertThat;"
                );

        assertThat(source)
                .contains(
                        "import static org.assertj.core.api.Assertions."
                                + "assertThatThrownBy;"
                );
    }

    @Test
    void shouldRenderMockitoMockForTestData() {

        ClassModel classModel = new ClassModel(
                "com.example.user",
                "UserService",
                List.of("Service"),
                List.of(
                        new DependencyModel(
                                "UserRepository",
                                "userRepository",
                                DependencyKind.MOCK
                        )
                ),
                List.of(),
                SpringType.SERVICE
        );

        TestScenario scenario =
                new TestScenario(
                        "create",
                        "create should execute successfully",
                        "User",
                        List.of(),
                        List.of(
                                new TestData(
                                        "savedUser",
                                        "User",
                                        "mock(User.class)"
                                )
                        ),
                        List.of(),
                        new ExpectedOutcome(
                                OutcomeKind.RETURN_VALUE,
                                "savedUser"
                        )
                );

        TestRenderModel renderModel =
                new TestRenderModel(
                        classModel.packageName(),
                        "UserServiceTest",
                        "UserService",
                        classModel.dependencies(),
                        List.of(scenario)
                );

        String source =
                renderer.render(renderModel);

        assertThat(source)
                .contains(
                        "import static org.mockito.Mockito.mock;"
                );

        assertThat(source)
                .contains(
                        "User savedUser = mock(User.class);"
                );
    }

}