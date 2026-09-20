package com.example.testgenerator.planning.model;

import java.util.List;

public record MockSetup(
        String dependency,
        String dependencyType,
        String method,
        List<String> arguments,
        MockAction action,
        String value
) {}