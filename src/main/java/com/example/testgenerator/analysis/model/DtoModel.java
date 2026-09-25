package com.example.testgenerator.analysis.model;

import java.util.List;

public record DtoModel(
        String qualifiedName,
        boolean hasBuilder,
        List<FieldModel> fields,
        boolean isInterface,
        boolean isAbstract,
        boolean hasNoArgConstructor,
        boolean hasAllArgsConstructor
) {}