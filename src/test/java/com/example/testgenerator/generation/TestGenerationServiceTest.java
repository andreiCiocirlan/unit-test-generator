package com.example.testgenerator.generation;

import com.example.testgenerator.analysis.JavaParserAnalyzer;
import com.example.testgenerator.analysis.model.ClassModel;
import com.example.testgenerator.analysis.model.DependencyModel;
import com.example.testgenerator.analysis.model.SpringType;
import com.example.testgenerator.execution.GeneratedTestValidator;
import com.example.testgenerator.generation.model.GeneratedTest;
import com.example.testgenerator.generation.model.TestRenderModel;
import com.example.testgenerator.planning.TestPlanner;
import com.example.testgenerator.planning.model.TestScenario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestGenerationServiceTest {

    @Mock
    private JavaParserAnalyzer analyzer;

    @Mock
    private TestPlanner planner;

    @Mock
    private JavaTestRenderer renderer;

    @Mock
    private GeneratedTestValidator validator;

    private TestGenerationService service;

    @BeforeEach
    void setUp() {

        service = new TestGenerationService(
                analyzer,
                planner,
                renderer,
                validator
        );
    }

    @Test
    void shouldGenerateTestSource() {

        Path sourceFile = Path.of(
                "src/test/resources/fixtures/UserService.java"
        );

        ClassModel classModel =
                createClassModel();

        TestScenario scenario =
                createScenario();

        String expectedSource =
                "generated test source";

        when(analyzer.analyze(sourceFile))
                .thenReturn(classModel);

        when(planner.plan(classModel))
                .thenReturn(List.of(scenario));

        when(renderer.render(
                any()
        ))
                .thenReturn(expectedSource);

        GeneratedTest result =
                service.generate(sourceFile);

        assertThat(result.source())
                .isEqualTo(expectedSource);

        verify(analyzer)
                .analyze(sourceFile);

        verify(planner)
                .plan(classModel);

        verify(renderer)
                .render(
                        any()
                );

        verify(validator)
                .validate(expectedSource);
    }

    @Test
    void shouldPassCorrectRenderModelToRenderer() {

        Path sourceFile = Path.of(
                "src/test/resources/fixtures/UserService.java"
        );

        ClassModel classModel =
                createClassModel();

        TestScenario scenario =
                createScenario();

        when(analyzer.analyze(sourceFile))
                .thenReturn(classModel);

        when(planner.plan(classModel))
                .thenReturn(List.of(scenario));

        when(renderer.render(any()))
                .thenReturn("generated source");

        service.generate(sourceFile);

        ArgumentCaptor<TestRenderModel> captor =
                ArgumentCaptor.forClass(
                        TestRenderModel.class
                );

        verify(renderer)
                .render(captor.capture());

        TestRenderModel renderModel =
                captor.getValue();

        assertThat(renderModel.packageName())
                .isEqualTo("com.example.orders");

        assertThat(renderModel.className())
                .isEqualTo("UserServiceTest");

        assertThat(renderModel.classUnderTest())
                .isEqualTo("UserService");

        assertThat(renderModel.dependencies())
                .hasSize(2);

        assertThat(renderModel.scenarios())
                .containsExactly(scenario);
    }

    @Test
    void shouldFailWhenNoScenariosAreGenerated() {

        Path sourceFile = Path.of(
                "src/test/resources/fixtures/UserService.java"
        );

        ClassModel classModel =
                createClassModel();

        when(analyzer.analyze(sourceFile))
                .thenReturn(classModel);

        when(planner.plan(classModel))
                .thenReturn(List.of());

        assertThatThrownBy(
                () -> service.generate(sourceFile)
        )
                .isInstanceOf(
                        IllegalStateException.class
                )
                .hasMessageContaining(
                        "No test scenarios generated"
                );

        verify(analyzer)
                .analyze(sourceFile);

        verify(planner)
                .plan(classModel);

        org.mockito.Mockito.verifyNoInteractions(
                renderer
        );
    }

    private ClassModel createClassModel() {

        return new ClassModel(
                "com.example.orders",
                "UserService",
                List.of("Service"),
                List.of(
                        new DependencyModel(
                                "UserRepository",
                                "userRepository",
                                com.example.testgenerator.analysis.model.DependencyKind.MOCK
                        ),
                        new DependencyModel(
                                "EmailService",
                                "emailService",
                                com.example.testgenerator.analysis.model.DependencyKind.MOCK
                        )
                ),
                List.of(),
                SpringType.SERVICE
        );
    }

    private TestScenario createScenario() {

        return new TestScenario(
                "create",
                "create should execute successfully",
                "User",
                List.of(),
                List.of(),
                List.of(),
                null
        );
    }
}