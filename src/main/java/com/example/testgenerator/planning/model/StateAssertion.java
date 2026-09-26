package com.example.testgenerator.planning.model;

/**
 * Post-call assertion on the state of a local variable.
 * Renders as: assertThat(local.getGetter()).isEqualTo(value);
 */
public record StateAssertion(
        String variableName,
        String getterName,
        String expectedValue
) {}