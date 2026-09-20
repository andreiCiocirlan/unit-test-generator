package com.example.testgenerator.analysis.model;

public record ThrowModel(
        String exceptionType,
        String expression,
        StatementContext context
) {}