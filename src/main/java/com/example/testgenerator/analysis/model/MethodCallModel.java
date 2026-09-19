package com.example.testgenerator.analysis.model;

import java.util.List;

public record MethodCallModel(
        String target,
        String methodName,
        List<String> arguments,
        CallKind kind
) {
}