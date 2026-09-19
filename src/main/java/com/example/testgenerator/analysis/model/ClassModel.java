package com.example.testgenerator.analysis.model;

import java.util.List;

public record ClassModel(
        String packageName,
        String className,
        List<String> annotations,
        List<DependencyModel> dependencies,
        List<MethodModel> methods,
        SpringType springType
) {
}