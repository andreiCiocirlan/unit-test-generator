package com.example.testgenerator.generation;

import com.example.testgenerator.execution.CompilationResult;
import com.example.testgenerator.execution.ExecutionResult;
import com.example.testgenerator.execution.GeneratedTestCompiler;
import com.example.testgenerator.execution.GeneratedTestExecutor;
import com.example.testgenerator.generation.model.GeneratedTest;
import com.example.testgenerator.generation.writer.TestSourceWriter;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
public class ProjectTestGenerationService {

    private final TestGenerationService generationService;
    private final TestSourceWriter sourceWriter;
    private final GeneratedTestCompiler compiler;
    private final GeneratedTestExecutor executor;

    public ProjectTestGenerationService(
            TestGenerationService generationService,
            TestSourceWriter sourceWriter,
            GeneratedTestCompiler compiler,
            GeneratedTestExecutor executor) {

        this.generationService = generationService;
        this.sourceWriter = sourceWriter;
        this.compiler = compiler;
        this.executor = executor;
    }

    public CompilationResult generateAndCompile(
            Path projectRoot,
            Path sourceFile) {

        GeneratedTest generatedTest =
                generationService.generate(
                        projectRoot,
                        sourceFile
                );

        Path generatedFile =
                sourceWriter.write(
                        generatedTest.packageName(),
                        generatedTest.className(),
                        generatedTest.source(),
                        projectRoot
                );

        return compiler.compile(
                generatedFile,
                projectRoot
        );
    }

    public ExecutionResult generateAndTest(
            Path projectRoot,
            Path sourceFile) {

        GeneratedTest generatedTest =
                generationService.generate(
                        projectRoot, sourceFile
                );

        Path generatedFile =
                sourceWriter.write(
                        generatedTest.packageName(),
                        generatedTest.className(),
                        generatedTest.source(),
                        projectRoot
                );

        CompilationResult compilation =
                compiler.compile(
                        generatedFile,
                        projectRoot
                );

        if (!compilation.successful()) {
            return new ExecutionResult(
                    false,
                    compilation.diagnostics()
            );
        }

        return executor.execute(
                generatedTest.className(),
                projectRoot
        );
    }
}