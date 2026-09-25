package com.example.testgenerator.planning.model;

public record ExpectedOutcome(
        OutcomeKind kind,
        String value,
        boolean identityExpected
) {
    // existing constructor for convenience
    public ExpectedOutcome(OutcomeKind kind, String value) {
        this(kind, value, false);
    }
}