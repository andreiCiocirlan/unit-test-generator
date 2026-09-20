package com.example.testgenerator.analysis.model;

import java.util.List;

public record ConditionModel(
        String expression,
        List<MethodCallModel> methodCalls,
        List<String> thrownExceptions,
        StatementContext context
) {}