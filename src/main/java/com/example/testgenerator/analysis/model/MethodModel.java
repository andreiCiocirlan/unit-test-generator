package com.example.testgenerator.analysis.model;

import java.util.List;

public record MethodModel(
        String name,
        String returnType,
        List<String> annotations,
        List<String> declaredThrows,
        List<ParameterModel> parameters,
        List<MethodCallModel> methodCalls,
        List<ConditionModel> conditions,
        List<TryModel> tries,
        List<ReturnModel> returns,
        List<AssignmentModel> assignments,
        List<ThrowModel> throwsStatements,
        List<ForEachModel> forEaches
) {}