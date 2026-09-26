package com.example.testgenerator.planning.model;

/**
 * A stub to set up before the call. Resolved from a condition's
 * expression tree.
 */
public record BranchSetup(
        String target,          // "invoice"
        String methodName,      // "getStatus"
        String value            // "\"PENDING\""
) {}