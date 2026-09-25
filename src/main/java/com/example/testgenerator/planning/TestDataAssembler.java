package com.example.testgenerator.planning;

import com.example.testgenerator.analysis.model.*;
import com.example.testgenerator.planning.model.GuardedField;
import com.example.testgenerator.planning.model.TestData;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Builds the list of local variables a generated test must declare in
 * its // Given block: parameters, mock results, and any assignments the
 * service makes that the test needs to reference.
 *
 * Knows nothing about scenarios, mock setups, or expected outcomes.
 */
public class TestDataAssembler {

    private final DefaultValueResolver valueResolver;

    public TestDataAssembler(DefaultValueResolver valueResolver) {
        this.valueResolver = valueResolver;
    }

    public List<TestData> assemble(
            MethodModel method,
            ConditionModel condition) {
        List<TestData> testData = new ArrayList<>();

        // Which parameter does this condition check for null (if any)?
        String nullCheckedParam =
                nullCheckedParameter(condition, method);

        GuardedField guarded = guardedField(condition, method);

        for (ParameterModel parameter : method.parameters()) {
            String initialization;

            if (parameter.name().equals(nullCheckedParam)) {
                initialization = guardTriggeringValue(parameter, condition);
            } else if (guarded != null
                       && guarded.parameterName().equals(parameter.name())) {
                initialization = parameterInitializerWithOverride(
                        parameter,
                        guarded.fieldName(),
                        guarded.value()
                );
            } else {
                initialization = parameterInitializer(parameter);
            }

            testData.add(new TestData(
                    parameter.name(),
                    parameter.type(),
                    initialization
            ));
        }

        for (MethodCallModel call : method.methodCalls()) {
            if (TypeValueSupport.isOptionalOrElseThrow(method, call)) {
                String variableName =
                        TypeValueSupport.expectedVariableName(method);

                // The variable holds the UNWRAPPED value of the Optional,
                // so its type and initializer must be the inner type's, not
                // Optional's.
                String innerType = optionalInnerType(method.returnType());

                testData.add(new TestData(
                        variableName,
                        innerType,
                        valueResolver.valueFor(innerType)
                ));
            }
        }

        for (AssignmentModel assignment : requiredAssignments(method)) {
            if (assignment.expression().isBlank()) {
                continue;
            }
            testData.add(new TestData(
                    assignment.variableName(),
                    assignment.variableType(),
                    createInitialization(assignment, method)
            ));
        }

        return reorderByDependency(testData);
    }

    /**
     * Reorder test data so that any variable referenced by another
     * variable's initializer is declared first. Uses Kahn's algorithm over
     * a dependency graph derived from whole-word references in the
     * initializers.
     *
     * Falls back to the original order for any variables involved in a
     * cycle, so the result is always a complete list.
     */
    private List<TestData> reorderByDependency(List<TestData> input) {

        Set<String> names = input.stream()
                .map(TestData::variableName)
                .collect(Collectors.toSet());

        // Build dependency map: for each TestData, the set of other
        // TestData names it references in its initializer.
        Map<String, Set<String>> deps = new LinkedHashMap<>();
        for (TestData td : input) {
            Set<String> referenced = new HashSet<>();
            for (String name : names) {
                if (name.equals(td.variableName())) continue;
                if (containsWholeWord(td.initialization(), name)) {
                    referenced.add(name);
                }
            }
            deps.put(td.variableName(), referenced);
        }

        List<TestData> ordered = new ArrayList<>();
        Set<String> emitted = new HashSet<>();
        List<TestData> remaining = new ArrayList<>(input);

        boolean progress = true;
        while (progress && !remaining.isEmpty()) {
            progress = false;
            Iterator<TestData> it = remaining.iterator();
            while (it.hasNext()) {
                TestData td = it.next();
                if (emitted.containsAll(deps.get(td.variableName()))) {
                    ordered.add(td);
                    emitted.add(td.variableName());
                    it.remove();
                    progress = true;
                }
            }
        }

        // Cycle or unresolved references: append the rest in original order.
        ordered.addAll(remaining);
        return ordered;
    }

    /**
     * True if `word` appears in `text` as a standalone identifier (word
     * boundaries on both sides). Prevents `pending` from matching inside
     * `pendingIndex`, and `limit` from matching inside `unlimited`.
     *
     * Also rejects matches preceded by a dot, so `repository.save(x)` does
     * not match a test-data variable named `save`.
     */
    private boolean containsWholeWord(String text, String word) {
        if (text == null || word == null || word.isEmpty()) {
            return false;
        }
        Pattern p = Pattern.compile(
                "(?<![.\\w])" + Pattern.quote(word) + "\\b"
        );
        return p.matcher(text).find();
    }

    private String parameterInitializerWithOverride(
            ParameterModel parameter,
            String fieldName,
            String overrideValue) {

        String type = parameter.type();

        if (TypeValueSupport.isWellKnownImmutable(type)) {
            // Scalars can't have DTO fields; fall back to the normal path.
            return parameterInitializer(parameter);
        }

        return valueResolver.valueFor(
                type,
                java.util.Map.of(fieldName, overrideValue)
        );
    }

    /**
     * If the guard expression inspects a getter on one of the method's
     * parameters (e.g. request.getRecipient()), return the parameter name,
     * the field name, and a literal value that makes the guard fire.
     * <p>
     * Handles the common shapes:
     *   Utils.isBlank(param.getX())        -> ""
     *   param.getX().isBlank()             -> ""
     *   param.getX().isEmpty()             -> ""
     *   !param.getX().isEmpty()            -> ""
     *   param.getX() == null               -> "null"
     */
    private GuardedField guardedField(
            ConditionModel condition,
            MethodModel method) {

        if (condition == null) return null;

        String expr = condition.expression();

        for (ParameterModel parameter : method.parameters()) {

            Pattern p = Pattern.compile(
                    "\\b" + Pattern.quote(parameter.name())
                    + "\\s*\\.\\s*get([A-Z][A-Za-z0-9_]*)\\s*\\(\\)"
            );
            var m = p.matcher(expr);
            if (!m.find()) continue;

            String field = m.group(1);
            String fieldName = Character.toLowerCase(field.charAt(0))
                               + field.substring(1);

            // Does the guard actually predicate on the getter's value?
            // i.e. `Utils.isBlank(param.getX())`, `param.getX().isBlank()`,
            // `param.getX().isEmpty()`, `param.getX() == null`, `!param.getX().isEmpty()`.
            // If instead the getter is just an argument to a dependency call
            // (`repository.existsByReference(param.getX())`), the field should
            // be populated normally and the dependency call stubbed.
            String getterCall = parameter.name() + ".get" + field + "()";

            boolean predicatesOnGetter =
                    Pattern.compile("\\b(isBlank|isEmpty)\\s*\\(\\s*"
                                    + Pattern.quote(getterCall) + "\\s*\\)")
                            .matcher(expr).find()
                    || expr.contains(getterCall + ".isBlank()")
                    || expr.contains(getterCall + ".isEmpty()")
                    || Pattern.compile("\\Q" + getterCall + "\\E\\s*==\\s*null")
                            .matcher(expr).find()
                    || Pattern.compile("\\Q" + getterCall + "\\E\\s*!=\\s*null")
                            .matcher(expr).find();

            if (!predicatesOnGetter) {
                continue;   // <-- the field is used but not predicated on
            }

            boolean wantsNull = Pattern.compile(
                    "\\Q" + getterCall + "\\E\\s*==\\s*null"
            ).matcher(expr).find();

            String value = wantsNull ? "null" : "\"\"";

            return new GuardedField(parameter.name(), fieldName, value);
        }

        return null;
    }

    /**
     * If the given type is Optional<T>, return T. Otherwise, return the
     * type unchanged. Used to describe the unwrapped value that orElseThrow
     * would produce.
     */
    private String optionalInnerType(String type) {
        if (type == null) return type;
        if (!type.startsWith("Optional<")) return type;

        int lt = type.indexOf('<');
        int gt = type.lastIndexOf('>');
        if (lt < 0 || gt < 0 || gt <= lt) return type;

        return type.substring(lt + 1, gt).trim();
    }

    private String guardTriggeringValue(
            ParameterModel parameter,
            ConditionModel condition) {

        String expr = condition.expression();
        String name = parameter.name();

        // If the guard specifically checks blank/empty, null triggers it too
        // (assuming the guard is `x == null || x.isBlank()`), so null is
        // always safe. But if we want a more targeted value, we can choose
        // "" for isBlank/isEmpty checks. Either works for `||` guards.
        boolean checksBlankish =
                Pattern.compile("\\b" + Pattern.quote(name)
                                + "\\s*\\.\\s*(isBlank|isEmpty)\\s*\\(")
                        .matcher(expr).find()
                || Pattern.compile("\\b(isBlank|isEmpty)\\s*\\(\\s*"
                                   + Pattern.quote(name) + "\\s*\\)")
                        .matcher(expr).find();

        if (checksBlankish) {
            // "" makes both isBlank and isEmpty true, and it also makes
            // `x == null` false — so only choose it when the guard uses `||`
            // with a null check, or when there is no null check at all.
            if (expr.contains(name + " == null")) {
                return "null";      // null satisfies both branches of `||`
            }
            return "\"\"";
        }

        return "null";
    }

    private String nullCheckedParameter(
            ConditionModel condition,
            MethodModel method) {

        if (condition == null) {
            return null;
        }

        String expr = condition.expression();

        for (ParameterModel parameter : method.parameters()) {
            if (parameterIsConstrained(expr, parameter.name())) {
                return parameter.name();
            }
        }
        return null;
    }


    /**
     * Does the guard expression constrain this parameter in a way that
     * a null / blank / empty value would trigger?
     */
    private boolean parameterIsConstrained(String expression, String name) {

        // status == null
        // status != null (negated form is also "constrained")
        // status.isBlank()
        // status.isEmpty()
        // !status.isEmpty()
        // NotificationUtils.isBlank(status)
        // Objects.isNull(status) / Objects.nonNull(status)

        // Direct comparisons.
        if (Pattern.compile("\\b" + Pattern.quote(name) + "\\s*==\\s*null\\b")
                .matcher(expression).find()) {
            return true;
        }
        if (Pattern.compile("\\b" + Pattern.quote(name) + "\\s*!=\\s*null\\b")
                .matcher(expression).find()) {
            return true;
        }

        // status.isBlank() / status.isEmpty() / status.isPresent() etc.
        if (Pattern.compile("\\b" + Pattern.quote(name) + "\\s*\\.\\s*(isBlank|isEmpty)\\s*\\(")
                .matcher(expression).find()) {
            return true;
        }

        // Wrapper forms: X.isBlank(status) / X.isEmpty(status) / Objects.isNull(status)
        if (Pattern.compile("\\b(isBlank|isEmpty|isNull|nonNull)\\s*\\(\\s*"
                            + Pattern.quote(name) + "\\s*\\)")
                .matcher(expression).find()) {
            return true;
        }

        return false;
    }

    private List<AssignmentModel> requiredAssignments(MethodModel method) {

        List<String> referenced = new ArrayList<>();

        method.returns().stream()
                .map(ReturnModel::expression)
                .forEach(referenced::add);

        method.methodCalls().stream()
                .flatMap(call -> call.arguments().stream())
                .forEach(referenced::add);

        method.throwsStatements().stream()
                .map(ThrowModel::expression)
                .forEach(referenced::add);

        method.conditions().stream()
                .map(ConditionModel::expression)
                .forEach(referenced::add);

        // NEW: assignment expressions — because one assignment may reference
        // another local (e.g. `notification = pending.get(i)` references
        // `pending`), and the reorder pass relies on both being present.
        method.assignments().stream()
                .map(AssignmentModel::expression)
                .forEach(referenced::add);

        return method.assignments().stream()
                .filter(a -> !a.expression().isBlank())
                .filter(a ->
                        TypeValueSupport.isPrimitiveType(a.variableType())
                        || TypeValueSupport.isWellKnownImmutable(a.variableType())
                        || referenced.stream().anyMatch(expr ->
                                Pattern.compile("\\b" + Pattern.quote(a.variableName()) + "\\b")
                                        .matcher(expr).find()))
                .toList();
    }

    private String createInitialization(AssignmentModel assignment, MethodModel method) {
        String expression = assignment.expression();
        String type = assignment.variableType();

        if (TypeValueSupport.isWellKnownImmutable(type)) {
            return TypeValueSupport.defaultValueFor(type);
        }

        // Real empty collections are almost always what you want as a
        // default in tests, and they compile.
        if (type.startsWith("List<") || type.startsWith("java.util.List<")) {

            if (isIterated(assignment, method)) {
                String elementType = TypeValueSupport.innerTypeOf(type);
                String elementName = elementVariableNameFor(
                        assignment.variableName(),
                        method
                );

                if (elementName != null) {
                    return "java.util.List.of(" + elementName + ")";
                }
            }

            return "java.util.List.of()";
        }
        if (type.startsWith("Set<") || type.startsWith("java.util.Set<")) {
            return "java.util.Set.of()";
        }
        if (type.startsWith("Map<") || type.startsWith("java.util.Map<")) {
            return "java.util.Map.of()";
        }

        if (expression.startsWith("new ")) {
            return expression;
        }

        if (expression.contains(".")) {
            // Prefer a real instance when possible.
            if (valueResolver.isInstantiableNoArg(type)) {
                return "new " + TypeValueSupport.simpleName(type) + "()";
            }
            if (valueResolver.isInstantiableAllArgs(type)) {
                String allArgs = valueResolver.allArgsConstructorInitializer(type);
                if (allArgs != null) return allArgs;
            }
            // Fall back to a mock.
            return "mock(" + TypeValueSupport.eraseGenerics(type) + ".class)";
        }

        return expression;
    }


    /**
     * Find the name of the variable that holds a single element of the
     * iterated collection. Matches assignments of the form
     *   <elementType> <name> = <collectionName>.get(...);
     * Returns null if no such assignment exists.
     */
    private String elementVariableNameFor(
            String collectionName,
            MethodModel method) {

        String needle = collectionName + ".get(";

        for (AssignmentModel a : method.assignments()) {
            if (a.expression().contains(needle)) {
                return a.variableName();
            }
        }
        return null;
    }

    /**
     * True if the collection variable is read in a way that implies
     * iteration: size(), get(...), forEach(...), stream(), iterator().
     */
    private boolean isIterated(
            AssignmentModel assignment,
            MethodModel method) {

        String varName = assignment.variableName();

        for (MethodCallModel call : method.methodCalls()) {
            if (!varName.equals(call.target())) continue;

            String m = call.methodName();
            if (m.equals("get")
                || m.equals("size")
                || m.equals("forEach")
                || m.equals("stream")
                || m.equals("iterator")) {
                return true;
            }
        }
        return false;
    }

    private String parameterInitializer(ParameterModel parameter) {
        String type = parameter.type();

        if (TypeValueSupport.isWellKnownImmutable(type)) {
            return TypeValueSupport.defaultValueFor(type);
        }

        return dtoInitializer(type);
    }

    private String dtoInitializer(String type) {
        return valueResolver.valueFor(type);
    }


}