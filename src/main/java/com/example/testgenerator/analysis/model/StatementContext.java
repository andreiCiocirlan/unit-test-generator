package com.example.testgenerator.analysis.model;

import java.util.List;

/**
 * Describes where a statement lives inside a method body.
 * All lists are ordered outermost-first.
 *
 * Example: a call inside an else-if of a try block will have
 *   ifConditions = ["order.getId() != null", "order.isPaid()"]
 *   tryDepth     = 1
 *   loopDepth    = 0
 */
public record StatementContext(
        List<String> ifConditions,
        int tryDepth,
        int loopDepth,
        boolean insideCatch,
        String caughtExceptionType
) {

    public static StatementContext topLevel() {
        return new StatementContext(List.of(), 0, 0, false, "");
    }

    public boolean isConditional() {
        return !ifConditions.isEmpty();
    }

    public boolean isInsideTry() {
        return tryDepth > 0;
    }
}