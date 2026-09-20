package com.example.testgenerator.analysis.model;

public record AssignmentModel(
        String variableName,
        String variableType,
        String expression,
        StatementContext context
) {}