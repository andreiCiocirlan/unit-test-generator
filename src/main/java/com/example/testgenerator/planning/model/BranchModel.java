package com.example.testgenerator.planning.model;

import java.util.List;

/**
 * A single control-flow branch worth testing.
 *
 * A BranchModel says: "to exercise this branch, arrange the mocks
 * like so; then the method returns/throws this."
 */
public record BranchModel(
        String methodName,
        String displayName,
        List<BranchSetup> setups,
        BranchOutcome outcome
) {}