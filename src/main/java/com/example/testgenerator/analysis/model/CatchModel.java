package com.example.testgenerator.analysis.model;

import java.util.List;

public record CatchModel(
        String exceptionType,
        String variableName,
        List<MethodCallModel> methodCalls,
        List<ReturnModel> returns,
        List<ThrowModel> throwsStatements
) {}