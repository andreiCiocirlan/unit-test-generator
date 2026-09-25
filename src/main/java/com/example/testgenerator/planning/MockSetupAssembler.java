package com.example.testgenerator.planning;

import com.example.testgenerator.analysis.model.AssignmentModel;
import com.example.testgenerator.analysis.model.CallKind;
import com.example.testgenerator.analysis.model.MethodCallModel;
import com.example.testgenerator.analysis.model.MethodModel;
import com.example.testgenerator.planning.model.MockAction;
import com.example.testgenerator.planning.model.MockSetup;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds MockSetup entries for a single dependency call, or for the set
 * of getter-style calls on locals that come from dependency results.
 *
 * Knows about Mockito semantics (when/thenReturn/thenThrow/verify) but
 * not about scenarios, guards, or test data.
 */
public class MockSetupAssembler {

    private final DefaultValueResolver valueResolver;

    public MockSetupAssembler(DefaultValueResolver valueResolver) {
        this.valueResolver = valueResolver;
    }

    /**
     * Returns one or more setups for the given dependency call:
     *   - a RETURN setup if the call's result is used (assigned or returned)
     *   - a THROW setup is NOT emitted here — that's the catch scenario's job
     *   - a VERIFY setup is always included so the happy path checks the call
     */
    public List<MockSetup> forDependencyCall(
            MethodCallModel call,
            MethodModel method) {
        List<MockSetup> setups = new ArrayList<>();

        // Always verify dependency calls in the happy path.
        MockSetup verifySetup = new MockSetup(
                call.target(),
                call.targetType(),
                call.methodName(),
                normalizeArguments(call.arguments(), method),
                MockAction.VERIFY,
                ""
        );

        if (TypeValueSupport.isOptionalOrElseThrow(method, call)) {
            setups.add(new MockSetup(
                    call.target(),
                    call.targetType(),
                    call.methodName(),
                    normalizeArguments(call.arguments(), method),
                    MockAction.RETURN,
                    "java.util.Optional.of("
                    + TypeValueSupport.expectedVariableName(method)
                    + ")"
            ));
            setups.add(verifySetup);
            return setups;
        }

        String value = findReturnValue(call, method);

        if (!"null".equals(value)) {
            setups.add(new MockSetup(
                    call.target(),
                    call.targetType(),
                    call.methodName(),
                    normalizeArguments(call.arguments(), method),
                    MockAction.RETURN,
                    value
            ));
            setups.add(verifySetup);
            return setups;
        }

        if (callResultIsUsed(call, method)) {
            setups.add(new MockSetup(
                    call.target(),
                    call.targetType(),
                    call.methodName(),
                    normalizeArguments(call.arguments(), method),
                    MockAction.RETURN,
                    resolveReturnValue(method.returnType())
            ));
            setups.add(verifySetup);
            return setups;
        }

        // Pure side-effect call: verify only.
        setups.add(verifySetup);
        return setups;
    }

    private String resolveReturnValue(String type) {
        if (valueResolver.isInstantiableNoArg(type)) {
            return "new " + TypeValueSupport.simpleName(type) + "()";
        }
        if (valueResolver.isInstantiableAllArgs(type)) {
            String init = valueResolver.allArgsConstructorInitializer(type);
            if (init != null) return init;
        }
        return TypeValueSupport.defaultValueFor(type);
    }

    public List<MockSetup> forResultGetters(
            MethodModel method,
            Set<String> alreadyHandledKeys) {
        List<MockSetup> setups = new ArrayList<>();

        // 1. Find locals bound to a dependency call:
        //    <Type> <var> = <dep>.<method>(...)
        Set<String> dependencyResultLocals = new HashSet<>();
        for (AssignmentModel assignment : method.assignments()) {
            for (MethodCallModel call : method.methodCalls()) {
                if (call.kind() != CallKind.DEPENDENCY) continue;

                String needle = call.target() + "." + call.methodName() + "(";
                if (assignment.expression().contains(needle)) {
                    dependencyResultLocals.add(assignment.variableName());
                }
            }
        }

        if (dependencyResultLocals.isEmpty()) {
            return setups;
        }

        // 2. For calls on those locals, stub a return value.
        for (MethodCallModel call : method.methodCalls()) {
            if (!dependencyResultLocals.contains(call.target())) continue;
            if (alreadyHandledKeys.contains(callKey(call))) continue;
            if (!call.arguments().isEmpty()) continue;

            // Skip calls on locals whose declared type is a collection or
            // scalar — those are initialized as real objects, not mocks, so
            // they can't be stubbed.
            String localType = declaredTypeOf(call.target(), method);
            if (TypeValueSupport.isCollectionType(localType) || TypeValueSupport.isWellKnownImmutable(localType)) {
                continue;
            }

            String name = call.methodName();
            if (!name.startsWith("is")
                && call.context() != null
                && !call.context().ifConditions().isEmpty()) {
                continue;
            }

            String value = TypeValueSupport.defaultReturnForGetter(call);
            if (value == null) continue;

            setups.add(new MockSetup(
                    call.target(),
                    call.targetType(),
                    call.methodName(),
                    normalizeArguments(call.arguments(), method),
                    MockAction.RETURN,
                    value
            ));
        }

        return setups;
    }

    public String callKey(MethodCallModel call) {
        return call.target() + "."
               + call.methodName()
               + call.arguments();
    }

    public String findReturnValue(
            MethodCallModel call,
            MethodModel method) {

        return method.assignments().stream()
                .filter(a -> a.expression().contains(
                        call.target() + "." + call.methodName()))
                .map(AssignmentModel::variableName)
                .findFirst()
                .orElse("null");
    }

    public boolean callResultIsUsed(
            MethodCallModel call,
            MethodModel method) {

        String needle = call.target() + "." + call.methodName() + "(";

        // Assigned to a variable?
        boolean assigned = method.assignments().stream()
                .anyMatch(a -> a.expression().contains(needle));

        // Returned directly?
        boolean returned = method.returns().stream()
                .anyMatch(r -> r.expression().contains(needle));

        return assigned || returned;
    }

    public List<String> normalizeArguments(
            List<String> arguments,
            MethodModel method) {

        return arguments.stream()
                .map(a -> normalizeMockArgument(a, method))
                .toList();
    }

    private String normalizeMockArgument(
            String argument,
            MethodModel method) {

        return method.assignments().stream()
                .filter(a -> a.variableName().equals(argument))
                .filter(a -> a.expression().startsWith("new "))
                .map(a -> "any(" + a.variableType() + ".class)")
                .findFirst()
                .orElse(argument);
    }

    private String declaredTypeOf(String variableName, MethodModel method) {
        return method.assignments().stream()
                .filter(a -> a.variableName().equals(variableName))
                .map(AssignmentModel::variableType)
                .findFirst()
                .orElse("");
    }

}