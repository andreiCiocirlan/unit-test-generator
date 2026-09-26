package com.example.testgenerator.generation;

import com.example.testgenerator.analysis.model.DependencyModel;
import com.example.testgenerator.analysis.model.ParameterModel;
import com.example.testgenerator.generation.model.TestRenderModel;
import com.example.testgenerator.planning.model.*;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class JavaTestRenderer {

    public String render(TestRenderModel model) {

        StringBuilder source =
                new StringBuilder();

        renderPackage(source, model);
        renderImports(source);
        renderClass(source, model);

        return source.toString();
    }

    private void renderPackage(
            StringBuilder source,
            TestRenderModel model) {

        if (model.packageName() != null
                && !model.packageName().isBlank()) {

            source.append("package ")
                    .append(model.packageName())
                    .append(";\n\n");
        }
    }

    private void renderImports(
            StringBuilder source) {

        source.append("""
                import java.io.IOException;
                import java.math.BigDecimal;
                import java.util.List;
                import java.util.Optional;
                import org.junit.jupiter.api.Test;
                import org.junit.jupiter.api.extension.ExtendWith;

                import org.mockito.InjectMocks;
                import org.mockito.Mock;

                import static org.assertj.core.api.Assertions.assertThat;
                import static org.assertj.core.api.Assertions.assertThatThrownBy;
                import static org.mockito.ArgumentMatchers.any;
                import static org.mockito.Mockito.mock;
                import static org.mockito.Mockito.verify;
                import static org.mockito.Mockito.when;
                
                import org.mockito.junit.jupiter.MockitoExtension;

                """);
    }

    private void renderClass(
            StringBuilder source,
            TestRenderModel model) {

        source.append("@ExtendWith(MockitoExtension.class)\n");

        source.append("class ")
                .append(model.className())
                .append(" {\n\n");

        renderDependencies(source, model);
        renderClassUnderTest(source, model);

        for (TestScenario scenario :
                model.scenarios()) {

            renderScenario(
                    source,
                    model,
                    scenario
            );
        }

        source.append("}\n");
    }

    private void renderDependencies(
            StringBuilder source,
            TestRenderModel model) {

        for (DependencyModel dependency :
                model.dependencies()) {

            source.append("    @Mock\n");

            source.append("    private ")
                    .append(dependency.type())
                    .append(" ")
                    .append(dependency.name())
                    .append(";\n\n");
        }
    }

    private void renderClassUnderTest(
            StringBuilder source,
            TestRenderModel model) {

        source.append("    @InjectMocks\n");

        source.append("    private ")
                .append(model.classUnderTest())
                .append(" ")
                .append(toVariableName(
                        model.classUnderTest()
                ))
                .append(";\n\n");
    }

    private void renderScenario(
            StringBuilder source,
            TestRenderModel model,
            TestScenario scenario) {

        source.append("    @Test\n");

        source.append("    void ")
                .append(toMethodName(scenario.displayName()))
                .append("()");

        if (scenario.expectedOutcome().kind() != OutcomeKind.THROW_EXCEPTION
            && !scenario.declaredThrows().isEmpty()) {
            source.append(" throws ")
                    .append(String.join(", ", scenario.declaredThrows()));
        }

        source.append(" {\n\n");

        renderGiven(
                source,
                scenario
        );

        renderWhenThen(
                source,
                model,
                scenario
        );

        source.append("    }\n\n");
    }

    private void renderGiven(
            StringBuilder source,
            TestScenario scenario) {

        source.append("        // Given\n");

        renderTestData(
                source,
                scenario
        );

        for (MockSetup setup : scenario.mockSetups()) {

            if (setup.action() == MockAction.RETURN) {
                source.append("        when(")
                        .append(setup.dependency()).append(".")
                        .append(setup.method()).append("(")
                        .append(String.join(", ", setup.arguments()))
                        .append("))")
                        .append(".thenReturn(")
                        .append(setup.value())
                        .append(");\n");
                continue;
            }

            if (setup.action() == MockAction.THROW) {
                source.append("        when(")
                        .append(setup.dependency()).append(".")
                        .append(setup.method()).append("(")
                        .append(String.join(", ", setup.arguments()))
                        .append("))")
                        .append(".thenThrow(")
                        .append(setup.value())
                        .append(".class);\n");
            }
        }

        source.append("\n");
    }

    private void renderTestData(
            StringBuilder source,
            TestScenario scenario) {

        for (TestData data :
                scenario.testData()) {

            source.append("        ")
                    .append(data.type())
                    .append(" ")
                    .append(data.variableName())
                    .append(" = ")
                    .append(data.initialization())
                    .append(";\n");
        }

        if (!scenario.testData().isEmpty()) {
            source.append("\n");
        }
    }

    private void renderWhenThen(
            StringBuilder source,
            TestRenderModel model,
            TestScenario scenario) {

        ExpectedOutcome outcome =
                scenario.expectedOutcome();

        if (outcome.kind()
                == OutcomeKind.THROW_EXCEPTION) {

            renderExceptionScenario(
                    source,
                    model,
                    scenario,
                    outcome
            );

            return;
        }

        renderSuccessScenario(
                source,
                model,
                scenario,
                outcome
        );
    }

    private void renderExceptionScenario(
            StringBuilder source,
            TestRenderModel model,
            TestScenario scenario,
            ExpectedOutcome outcome) {

        source.append("        // When / Then\n");

        source.append("        assertThatThrownBy(() -> ")
                .append(toVariableName(
                        model.classUnderTest()
                ))
                .append(".")
                .append(scenario.methodName())
                .append("(")
                .append(renderMethodArguments(scenario))
                .append("))\n");

        source.append("                .isInstanceOf(")
                .append(outcome.value())
                .append(".class);\n");

        renderStateAssertions(source, outcome);

        renderVerifications(source, scenario);
    }

    private void renderSuccessScenario(
            StringBuilder source,
            TestRenderModel model,
            TestScenario scenario,
            ExpectedOutcome outcome) {

        source.append("        // When\n");

        if (outcome.kind()
            == OutcomeKind.RETURN_VALUE) {

            source.append("        ")
                    .append(scenario.returnType())
                    .append(" result = ")
                    .append(toVariableName(
                            model.classUnderTest()
                    ))
                    .append(".")
                    .append(scenario.methodName())
                    .append("(")
                    .append(renderMethodArguments(scenario))
                    .append(");\n\n");

            source.append("        // Then\n");

            source.append("        assertThat(result)\n")
                    .append(outcome.identityExpected()
                            ? "                .isSameAs("
                            : "                .isEqualTo(")
                    .append(outcome.value())
                    .append(");\n");

        } else {
            // void case
            source.append("        ")
                    .append(toVariableName(model.classUnderTest()))
                    .append(".").append(scenario.methodName())
                    .append("(").append(renderMethodArguments(scenario)).append(");\n\n");
            source.append("        // Then\n");
        }


        renderStateAssertions(source, outcome);

        renderVerifications(source, scenario);
    }

    private void renderStateAssertions(
            StringBuilder source,
            ExpectedOutcome outcome) {

        if (outcome.stateAssertions() == null
            || outcome.stateAssertions().isEmpty()) {
            return;
        }

        source.append("\n");

        for (StateAssertion a : outcome.stateAssertions()) {
            source.append("        assertThat(")
                    .append(a.variableName())
                    .append(".")
                    .append(a.getterName())
                    .append("())\n");
            source.append("                .isEqualTo(")
                    .append(a.expectedValue())
                    .append(");\n");
        }
    }

    private void renderVerifications(
            StringBuilder source,
            TestScenario scenario) {

        for (MockSetup setup :
                scenario.mockSetups()) {

            if (setup.action()
                    != MockAction.VERIFY) {
                continue;
            }

            source.append("\n");

            source.append("        verify(")
                    .append(setup.dependency())
                    .append(")")
                    .append(".")
                    .append(setup.method())
                    .append("(")
                    .append(String.join(
                            ", ",
                            setup.arguments()
                    ))
                    .append(");\n");
        }
    }

    private String renderMethodArguments(
            TestScenario scenario) {

        return scenario.parameters()
                .stream()
                .map(ParameterModel::name)
                .collect(Collectors.joining(", "));
    }

    private String toVariableName(
            String className) {

        if (className == null
                || className.isBlank()) {

            return className;
        }

        return Character.toLowerCase(
                className.charAt(0)
        ) + className.substring(1);
    }

    private String toMethodName(String displayName) {

        String methodName =
                displayName
                        .replaceAll(
                                "[^a-zA-Z0-9]+",
                                "_"
                        )
                        .replaceAll(
                                "_+",
                                "_"
                        )
                        .replaceAll(
                                "^_|_$",
                                ""
                        );

        if (methodName.isEmpty()) {
            return "generatedTest";
        }

        return Character.toLowerCase(
                methodName.charAt(0)
        ) + methodName.substring(1);
    }
}