package com.example.testgenerator.planning.model;

import java.util.List;

/**
 * What the method does on this branch.
 */
public record BranchOutcome(
        BranchOutcomeKind kind,
        String value,           // return expression or exception class
        List<StateAssertion> stateAssertions
) {}

