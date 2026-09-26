package com.example.testgenerator.analysis.model;

public record ForEachModel(
        String collectionName,
        String elementName,
        String elementType,
        StatementContext context
) {}