package com.example.testgenerator.analysis.model;

import java.util.List;

public record TryModel(
        List<MethodCallModel> bodyCalls,
        List<ReturnModel> bodyReturns,
        List<ThrowModel> bodyThrows,
        List<CatchModel> catches,
        List<MethodCallModel> finallyCalls
) {}