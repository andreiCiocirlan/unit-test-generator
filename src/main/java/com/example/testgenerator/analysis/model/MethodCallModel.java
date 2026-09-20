package com.example.testgenerator.analysis.model;

import java.util.List;

public record MethodCallModel(
        String target,
        String targetType,
        String receiverChain,
        String methodName,
        List<String> arguments,
        CallKind kind,
        StatementContext context
) {
    public boolean isDependencyCall() {
        return kind == CallKind.DEPENDENCY;
    }

    public boolean isInternalCall() {
        return kind == CallKind.INTERNAL;
    }

    /** Convenience for callers that don't care about context. */
    public MethodCallModel withoutContext() {
        return new MethodCallModel(
                target, targetType, receiverChain,
                methodName, arguments, kind,
                StatementContext.topLevel()
        );
    }
}