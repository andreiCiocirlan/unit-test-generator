package com.example.testgenerator.analysis.model;

public record DependencyModel(
        String type,
        String name,
        DependencyKind kind
) {
}