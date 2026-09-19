package com.example.testgenerator.analysis.model;

import java.util.List;

public record MethodModel(
        String name,
        String returnType,
        List<ParameterModel> parameters,
        List<MethodCallModel> methodCalls,
        List<ConditionModel> conditions,
        List<ReturnModel> returns,
        List<AssignmentModel> assignments,
        List<ThrowModel> throwsStatements
) {
}