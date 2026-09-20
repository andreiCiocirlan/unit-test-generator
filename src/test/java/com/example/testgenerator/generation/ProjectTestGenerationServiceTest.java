package com.example.testgenerator.generation;

import com.example.testgenerator.execution.CompilationResult;
import com.example.testgenerator.execution.GeneratedTestCompiler;
import com.example.testgenerator.execution.GeneratedTestExecutor;
import com.example.testgenerator.execution.ExecutionResult;
import com.example.testgenerator.generation.model.GeneratedTest;
import com.example.testgenerator.generation.writer.TestSourceWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectTestGenerationServiceTest {

    @Mock
    private TestGenerationService generationService;

    @Mock
    private TestSourceWriter sourceWriter;

    @Mock
    private GeneratedTestCompiler compiler;

    @Mock
    private GeneratedTestExecutor executor;

    @InjectMocks
    private ProjectTestGenerationService service;

    @Test
    void shouldGenerateCompileAndExecuteTest() {

        Path projectRoot =
                Path.of("project");

        Path sourceFile =
                Path.of("UserService.java");

        GeneratedTest generatedTest =
                new GeneratedTest(
                        "com.example.user",
                        "UserServiceTest",
                        "generated source"
                );

        Path generatedFile =
                Path.of("UserServiceTest.java");

        when(generationService.generate(sourceFile))
                .thenReturn(generatedTest);

        when(sourceWriter.write(
                "com.example.user",
                "UserServiceTest",
                "generated source",
                projectRoot
        )).thenReturn(generatedFile);

        when(compiler.compile(
                generatedFile,
                projectRoot
        )).thenReturn(
                new CompilationResult(
                        true,
                        List.of()
                )
        );

        when(executor.execute(
                "UserServiceTest",
                projectRoot
        )).thenReturn(
                new ExecutionResult(
                        true,
                        List.of()
                )
        );

        ExecutionResult result =
                service.generateAndTest(
                        projectRoot,
                        sourceFile
                );

        assertThat(result.successful())
                .isTrue();

        assertThat(result.diagnostics())
                .isEmpty();

        verify(generationService)
                .generate(sourceFile);

        verify(sourceWriter)
                .write(
                        "com.example.user",
                        "UserServiceTest",
                        "generated source",
                        projectRoot
                );

        verify(compiler)
                .compile(
                        generatedFile,
                        projectRoot
                );

        verify(executor)
                .execute(
                        "UserServiceTest",
                        projectRoot
                );
    }

    @Test
    void shouldNotExecuteWhenCompilationFails() {

        Path projectRoot =
                Path.of("project");

        Path sourceFile =
                Path.of("UserService.java");

        GeneratedTest generatedTest =
                new GeneratedTest(
                        "com.example.user",
                        "UserServiceTest",
                        "generated source"
                );

        Path generatedFile =
                Path.of("UserServiceTest.java");

        when(generationService.generate(sourceFile))
                .thenReturn(generatedTest);

        when(sourceWriter.write(
                "com.example.user",
                "UserServiceTest",
                "generated source",
                projectRoot
        )).thenReturn(generatedFile);

        when(compiler.compile(
                generatedFile,
                projectRoot
        )).thenReturn(
                new CompilationResult(
                        false,
                        List.of("compilation error")
                )
        );

        ExecutionResult result =
                service.generateAndTest(
                        projectRoot,
                        sourceFile
                );

        assertThat(result.successful())
                .isFalse();

        assertThat(result.diagnostics())
                .containsExactly(
                        "compilation error"
                );

        verify(generationService)
                .generate(sourceFile);

        verify(sourceWriter)
                .write(
                        "com.example.user",
                        "UserServiceTest",
                        "generated source",
                        projectRoot
                );

        verify(compiler)
                .compile(
                        generatedFile,
                        projectRoot
                );

        verifyNoInteractions(executor);
    }
}