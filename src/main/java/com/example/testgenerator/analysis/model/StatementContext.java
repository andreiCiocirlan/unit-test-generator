package com.example.testgenerator.analysis.model;

import java.util.List;

public record StatementContext(
        List<String> ifConditions,
        int tryDepth,
        int loopDepth,
        boolean insideCatch,
        String caughtExceptionType,
        BranchPosition branchPosition
) {

    public static StatementContext topLevel() {
        return new StatementContext(List.of(), 0, 0, false, "", BranchPosition.NONE);
    }

    public boolean isConditional() {
        return !ifConditions.isEmpty();
    }

    public boolean isInsideTry() {
        return tryDepth > 0;
    }

    public enum BranchPosition {
        /** Not inside any if-statement. */
        NONE,
        /** Inside the then-block of the nearest if. */
        THEN,
        /** Inside the else-block of the nearest if. */
        ELSE,
        /** Inside the else-if of the nearest if. */
        ELSE_IF
    }
}