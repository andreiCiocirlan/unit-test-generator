package com.example.testgenerator.execution;

import java.nio.file.Path;

public interface GeneratedTestCompiler {

    CompilationResult compile(
            Path sourceFile,
            Path projectRoot
    );
}