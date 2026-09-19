package com.example.testgenerator.generation;

import com.example.testgenerator.execution.CompilationResult;
import com.example.testgenerator.execution.GeneratedTestCompiler;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectTestGenerationServiceTest {

    @Test
    void shouldGenerateAndCompileUserServiceTest() {

        Path projectRoot =
                Path.of(
                        "C:\\Users\\andre\\IdeaProjects\\book"
                );

        Path sourceFile =
                projectRoot.resolve(
                        "src/main/java/com/example/user/UserService.java"
                );

        // We'll use the real Spring components here.
        var analyzer =
                new com.example.testgenerator.analysis.JavaParserAnalyzer(
                        new com.example.testgenerator.analysis.SpringTypeClassifier(),
                        new com.example.testgenerator.analysis.ConstructorResolver(),
                        new com.example.testgenerator.analysis.JavaParserMethodCallAnalyzer(),
                        new com.example.testgenerator.analysis.JavaParserConditionAnalyzer()
                );

        var generationService =
                new TestGenerationService(
                        analyzer,
                        new com.example.testgenerator.planning.DefaultTestPlanner(),
                        new JavaTestRenderer(),
                        new com.example.testgenerator.execution.JavaParserGeneratedTestValidator()
                );

        var sourceWriter =
                new JavaTestSourceWriter();

        GeneratedTestCompiler compiler =
                new com.example.testgenerator.execution.MavenGeneratedTestCompiler();

        var service =
                new ProjectTestGenerationService(
                        generationService,
                        sourceWriter,
                        compiler
                );

        CompilationResult result =
                service.generateAndCompile(
                        projectRoot,
                        sourceFile
                );

        assertThat(result.successful())
                .withFailMessage(
                        () -> String.join(
                                "\n",
                                result.diagnostics()
                        )
                )
                .isTrue();
    }
}