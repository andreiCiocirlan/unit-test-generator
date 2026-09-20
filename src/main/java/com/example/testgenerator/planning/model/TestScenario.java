package com.example.testgenerator.planning.model;

import com.example.testgenerator.analysis.model.ParameterModel;

import java.util.List;

public record TestScenario(
        String methodName,
        String displayName,
        String returnType,
        List<String> declaredThrows,
        List<ParameterModel> parameters,
        List<TestData> testData,
        List<MockSetup> mockSetups,
        ExpectedOutcome expectedOutcome
) {}