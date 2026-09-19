package com.example.testgenerator.generation;

import com.example.testgenerator.execution.CompilationResult;
import com.example.testgenerator.execution.GeneratedTestCompiler;
import com.example.testgenerator.generation.model.GeneratedTest;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
public class ProjectTestGenerationService {

    private final TestGenerationService generationService;
    private final TestSourceWriter sourceWriter;
    private final GeneratedTestCompiler compiler;

    public ProjectTestGenerationService(
            TestGenerationService generationService,
            TestSourceWriter sourceWriter,
            GeneratedTestCompiler compiler) {

        this.generationService = generationService;
        this.sourceWriter = sourceWriter;
        this.compiler = compiler;
    }

    public CompilationResult generateAndCompile(
            Path projectRoot,
            Path sourceFile) {

        GeneratedTest generatedTest =
                generationService.generate(
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
}