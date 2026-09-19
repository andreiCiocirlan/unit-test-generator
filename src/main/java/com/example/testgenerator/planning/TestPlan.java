package com.example.testgenerator.planning;

import com.example.testgenerator.analysis.model.DependencyModel;

import java.util.List;

public record TestPlan(
        String packageName,
        String className,
        String classUnderTest,
        List<DependencyModel> dependencies
) {
}