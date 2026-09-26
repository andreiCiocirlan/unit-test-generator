package com.example.testgenerator.planning;

import com.example.testgenerator.analysis.model.MethodCallModel;
import com.example.testgenerator.analysis.model.MethodModel;
import com.example.testgenerator.planning.model.StateAssertion;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Scans a method body for `local.setX(value)` calls on locals that are
 * declared in the test's Given block, and turns each into a post-call
 * state assertion `assertThat(local.getX()).isEqualTo(value)`.
 *
 * Only setter calls on locals are considered. Setter calls on
 * dependencies (e.g. `repository.setSomething(...)`) are interactions,
 * not state, and are already covered by verify().
 */
public class StateAssertionCollector {

    /** Collect from every call in the method body. */
    public List<StateAssertion> collect(MethodModel method) {
        Set<String> localNames = new HashSet<>();
        for (var a : method.assignments()) {
            localNames.add(a.variableName());
        }
        // Happy path: skip catch-block setters.
        return collectFrom(method.methodCalls(), localNames, true);
    }

    public List<StateAssertion> collectFrom(List<MethodCallModel> calls) {
        // Catch body: don't skip, they're all we want.
        return collectFrom(calls, null, false);
    }

    private List<StateAssertion> collectFrom(
            List<MethodCallModel> calls,
            Set<String> allowedLocals,
            boolean skipCatchSetters) {

        List<StateAssertion> assertions = new ArrayList<>();

        for (MethodCallModel call : calls) {

            String name = call.methodName();
            if (!name.startsWith("set")) continue;
            if (call.arguments().size() != 1) continue;

            String target = call.target();
            if (target == null || target.isBlank()) continue;

            if (allowedLocals != null && !allowedLocals.contains(target)) {
                continue;
            }

            if (skipCatchSetters
                && call.context() != null
                && call.context().insideCatch()) {
                continue;
            }

            String property = name.substring(3);
            if (property.isEmpty()) continue;

            String getter = "get" + property;
            String value = call.arguments().get(0);

            assertions.add(new StateAssertion(target, getter, value));
        }

        return assertions;
    }
}