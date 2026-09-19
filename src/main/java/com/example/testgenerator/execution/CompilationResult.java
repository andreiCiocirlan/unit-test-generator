package com.example.testgenerator.execution;

import java.util.List;

public record CompilationResult(
        boolean successful,
        List<String> diagnostics
) {
}