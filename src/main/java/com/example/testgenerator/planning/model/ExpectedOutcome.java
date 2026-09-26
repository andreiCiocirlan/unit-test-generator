package com.example.testgenerator.planning.model;

import java.util.List;

public record ExpectedOutcome(
        OutcomeKind kind,
        String value,
        boolean identityExpected,
        List<StateAssertion> stateAssertions
) {
    public ExpectedOutcome(OutcomeKind kind, String value) {
        this(kind, value, false, List.of());
    }

    public ExpectedOutcome(OutcomeKind kind, String value, boolean identityExpected) {
        this(kind, value, identityExpected, List.of());
    }
}