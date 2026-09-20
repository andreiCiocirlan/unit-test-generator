package com.example.testgenerator.analysis.model;

import java.util.List;

public record MethodCallModel(
        /* Root receiver name: "paymentClient", "order", "this"→"", etc. */
        String target,

        /* Declared type of the root receiver, if resolvable. May be "". */
        String targetType,

        /* Full receiver chain as source text, e.g. "order.getUser()". */
        String receiverChain,

        /* Name of the method being invoked. */
        String methodName,

        /* Argument expressions as source text. */
        List<String> arguments,

        /* Classification for test-generation purposes. */
        CallKind kind
) {
    public boolean isDependencyCall() {
        return kind == CallKind.DEPENDENCY;
    }

    public boolean isInternalCall() {
        return kind == CallKind.INTERNAL;
    }
}