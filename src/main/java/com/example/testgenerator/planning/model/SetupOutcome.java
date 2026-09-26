package com.example.testgenerator.planning.model;

import java.util.List;

public record SetupOutcome(
        List<BranchSetup> setups,
        String expectedValue,
        String displayName
) {}