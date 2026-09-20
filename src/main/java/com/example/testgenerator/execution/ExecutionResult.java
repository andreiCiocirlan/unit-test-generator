package com.example.testgenerator.execution;

import java.util.List;

public record ExecutionResult(
        boolean successful,
        List<String> diagnostics
) {}