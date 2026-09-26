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

        boolean insideLoopConditional =
                call.context() != null
                && call.context().loopDepth() > 0
                && !call.context().ifConditions().isEmpty();

        // RETURN setup (if any) — always emitted when the result is used.
        setups.addAll(returnSetupsFor(call, method));

        // VERIFY setup — skipped inside loop-body conditionals.
        if (!insideLoopConditional) {
            setups.add(new MockSetup(
                    call.target(),
                    call.targetType(),
                    call.methodName(),
                    matcherArguments(call.arguments(), method),
                    MockAction.VERIFY,
                    ""
            ));
        }

        return setups;
    }

    private List<MockSetup> returnSetupsFor(
            MethodCallModel call,
            MethodModel method) {

        List<MockSetup> setups = new ArrayList<>();

        if (TypeValueSupport.isOptionalOrElseThrow(method, call)) {
            setups.add(new MockSetup(
                    call.target(),
                    call.targetType(),
                    call.methodName(),
                    matcherArguments(call.arguments(), method),
                    MockAction.RETURN,
                    "java.util.Optional.of("
                    + TypeValueSupport.expectedVariableName(method)
                    + ")"
            ));
            return setups;
        }

        String value = findReturnValue(call, method);

        if (!"null".equals(value)) {
            setups.add(new MockSetup(
                    call.target(),
                    call.targetType(),
                    call.methodName(),
                    matcherArguments(call.arguments(), method),
                    MockAction.RETURN,
                    value
            ));
            return setups;
        }

        if (callResultIsUsed(call, method)) {
            setups.add(new MockSetup(
                    call.target(),
                    call.targetType(),
                    call.methodName(),
                    matcherArguments(call.arguments(), method),
                    MockAction.RETURN,
                    TypeValueSupport.defaultValueFor(method.returnType())
            ));
        }

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

            // NEW: skip locals that are initialized as real instances.
            // `new Type()` is not a mock, so `when(...)` on its getters
            // throws MissingMethodInvocationException.
            if (valueResolver.isInstantiableNoArg(localType)
                || valueResolver.isInstantiableAllArgs(localType)) {
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
                    matcherArguments(call.arguments(), method),
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

    public List<String> matcherArguments(
            List<String> arguments,
            MethodModel method) {

        return arguments.stream()
                .map(a -> toMatcher(a, method))
                .toList();
    }

    private String declaredTypeOf(String variableName, MethodModel method) {
        return method.assignments().stream()
                .filter(a -> a.variableName().equals(variableName))
                .map(AssignmentModel::variableType)
                .findFirst()
                .orElse("");
    }

    /**
     * Convert a raw argument expression into its Mockito matcher form.
     *
     *   - parameter with a known test value -> eq(expr)
     *   - literal -> eq(expr)
     *   - anything else -> any()
     */
    private String toMatcher(String argument, MethodModel method) {

        String a = argument.trim();

        // Literals
        if (a.equals("null")
            || a.equals("true")
            || a.equals("false")
            || a.matches("-?\\d+[LlFfDd]?")
            || a.matches("-?\\d+\\.\\d+[FfDd]?")
            || (a.startsWith("\"") && a.endsWith("\""))) {
            return "eq(" + a + ")";
        }

        // Bare parameter name
        boolean isParameter = method.parameters().stream()
                .anyMatch(p -> p.name().equals(a));
        if (isParameter) {
            return "eq(" + a + ")";
        }

        // Bare local variable name (declared in the test's Given block).
        // Only use eq(local) when the local is bound to a dependency-call
        // result — those are the instances the test and the service share
        // via stubs. Locals constructed independently (e.g. `new User(email)`)
        // refer to different objects in the test and in the service, so
        // eq(...) would not match; use any() instead.
        boolean isLocal = method.assignments().stream()
                .anyMatch(assign -> assign.variableName().equals(a));
        if (isLocal) {
            boolean boundToDependency = isBoundToDependencyCall(a, method);
            return boundToDependency ? "eq(" + a + ")" : "any()";
        }

        // Anything else — a getter call, a nested expression — depends on
        // service state, so use any().
        return "any()";
    }

    private boolean isBoundToDependencyCall(
            String localName,
            MethodModel method) {

        return method.assignments().stream()
                .filter(assign -> assign.variableName().equals(localName))
                .anyMatch(assign -> method.methodCalls().stream()
                        .anyMatch(c -> c.kind() == CallKind.DEPENDENCY
                                       && assign.expression().contains(
                                c.target() + "." + c.methodName() + "(")));
    }
}