package com.example.testgenerator.generation.model;

import com.example.testgenerator.analysis.model.DependencyModel;
import com.example.testgenerator.planning.model.TestScenario;

import java.util.List;

public record TestRenderModel(
        String packageName,
        String className,
        String classUnderTest,
        List<DependencyModel> dependencies,
        List<TestScenario> scenarios
) {}