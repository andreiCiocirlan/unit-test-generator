package com.example.testgenerator.planning;

import com.example.testgenerator.analysis.ExpressionParser;
import com.example.testgenerator.analysis.model.*;
import com.example.testgenerator.planning.model.*;

import java.util.ArrayList;
import java.util.List;

public class BranchAnalyzer {

    private final ExpressionParser exprParser = new ExpressionParser();
    private final ConditionStubResolver stubResolver = new ConditionStubResolver();

    public List<BranchModel> analyze(MethodModel method) {

        List<BranchModel> branches = new ArrayList<>();

        // 1. If/else-if/else chain (the method body after the guards)
        List<BranchModel> chain = chainScenarios(method);
        if (!chain.isEmpty()) {
            branches.addAll(chain);
            return branches;
        }

        // 2. Body-return decomposition
        if (!method.returns().isEmpty()) {
            ReturnModel bodyReturn = method.returns().getLast();
            boolean topLevel = bodyReturn.context() == null
                               || bodyReturn.context().ifConditions().isEmpty();

            if (topLevel) {
                branches.addAll(branchesForReturn(method, bodyReturn));
            }
        }

        return branches;
    }

    /**
     * If the method's body is a chain of if / else-if / else that all
     * return, produce one BranchModel per segment. Otherwise return empty.
     *
     * A "chain" here means: two or more returns, all at the top level
     * (same tryDepth and insideCatch), whose enclosing if-conditions
     * form a prefix-nested chain like:
     *
     *     if (A) return x;
     *     else if (B) return y;
     *     else return z;
     *
     * The returns' contexts are:
     *     x -> ifConditions = [A],                          position=THEN
     *     y -> ifConditions = [A, B],                       position=THEN
     *     z -> ifConditions = [A, B],                       position=ELSE
     */
    private List<BranchModel> chainScenarios(MethodModel method) {

        // Only consider top-level returns (not inside try/catch/loops).
        List<ReturnModel> topLevelReturns = method.returns().stream()
                .filter(r -> r.context() == null
                             || (r.context().tryDepth() == 0
                                 && !r.context().insideCatch()))
                .toList();

        if (topLevelReturns.size() < 2) return List.of();

        // Every top-level return must have at least one if-condition in
        // context — otherwise it's the method's unconditional fallthrough
        // return and doesn't participate in the chain.
        boolean anyWithoutIf = topLevelReturns.stream()
                .anyMatch(r -> r.context() == null
                               || r.context().ifConditions().isEmpty());

        if (anyWithoutIf) return List.of();

        // Every return's branch position must be THEN or ELSE — not ELSE_IF,
        // because ELSE_IF returns belong to the nested if, and their own
        // return statement is inside that nested if's then-block. Wait —
        // in a chain like `if (A) ... else if (B) ... else ...`, the
        // return for B sits inside B's then-block, but B's if-statement
        // is itself the else of A. From B's return's point of view, its
        // nearest IfStmt is B, and B is inside A's else. So positionInIf
        // for B's return reports THEN (relative to B). That's correct.
        //
        // So we accept any position; the conditions in context determine
        // the branch.
        List<BranchModel> branches = new ArrayList<>();

        for (ReturnModel r : topLevelReturns) {

            List<String> conditions = r.context() == null
                    ? List.of()
                    : r.context().ifConditions();

            if (conditions.isEmpty()) continue;

            // The "current" branch condition is the last one in the
            // innermost-first chain (JavaParser's ifConditions is
            // outermost-first, so the last element is the innermost).
            String currentCondition = conditions.get(conditions.size() - 1);

            // Is this return inside the ELSE of its nearest if?
            boolean isElseBranch = r.context() != null
                                   && r.context().branchPosition()
                                      == com.example.testgenerator.analysis.model.StatementContext.BranchPosition.ELSE;

            List<BranchSetup> setups;
            String displaySuffix;

            if (isElseBranch) {
                // The else-branch fires when all previous conditions are
                // false. We produce stubs that fail every condition in the
                // chain. For the shapes we handle (string equals + null
                // checks), a single stub value can usually fail all of them.
                setups = stubsForAllConditionsFalse(conditions, method);
                displaySuffix = "else_branch";
            } else {
                // The then-branch fires when its own condition is true.
                setups = stubsForConditionTrue(currentCondition, method);
                displaySuffix = shortName(currentCondition);
            }

            if (setups == null) continue;

            branches.add(new BranchModel(
                    method.name(),
                    method.name() + "_should_return_when_" + displaySuffix,
                    setups,
                    new BranchOutcome(
                            BranchOutcomeKind.RETURN,
                            r.expression(),
                            List.of()
                    )
            ));
        }

        return branches;
    }

    /**
     * Stubs that satisfy the given condition (make it true).
     */
    private List<BranchSetup> stubsForConditionTrue(
            String condition,
            MethodModel method) {

        try {
            ExprModel expr = exprParser.parse(condition);
            return stubResolver.resolve(expr, true);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Stubs that make every condition in the chain false. The order of
     * `conditions` is outermost-first, so the innermost is last.
     *
     * For a chain like [A, B, C] where all are `"LITERAL".equals(x.getY())`
     * or `x.getY() == null`, a single stub value that is none of the
     * literals and isn't null works. We compute it here from the set of
     * literals.
     */
    private List<BranchSetup> stubsForAllConditionsFalse(
            List<String> conditions,
            MethodModel method) {

        // Collect the target.getter and the set of literals from every
        // `"LITERAL".equals(target.getter())` condition.
        String target = null;
        String getter = null;
        java.util.Set<String> literals = new java.util.LinkedHashSet<>();
        boolean hasNullCheck = false;

        for (String c : conditions) {
            java.util.regex.Matcher eq = java.util.regex.Pattern.compile(
                    "^\"([^\"]*)\"\\.equals\\(([A-Za-z_][A-Za-z0-9_]*)\\.([A-Za-z_][A-Za-z0-9_]*)\\(\\)\\)$"
            ).matcher(c.trim());
            if (eq.find()) {
                target = eq.group(2);
                getter = eq.group(3);
                literals.add(eq.group(1));
                continue;
            }

            java.util.regex.Matcher nn = java.util.regex.Pattern.compile(
                    "^([A-Za-z_][A-Za-z0-9_]*)\\.([A-Za-z_][A-Za-z0-9_]*)\\(\\)\\s*==\\s*null$"
            ).matcher(c.trim());
            if (nn.find()) {
                target = nn.group(1);
                getter = nn.group(2);
                hasNullCheck = true;
                continue;
            }

            // Shape we don't understand — can't produce a stub.
            return null;
        }

        if (target == null || getter == null) return null;

        // Pick a value not in the set of literals, and non-null (so the
        // null check is also false).
        String value = "\"__other__\"";
        int counter = 0;
        while (literals.contains(value.replace("\"", ""))) {
            counter++;
            value = "\"__other" + counter + "__\"";
        }

        return List.of(new BranchSetup(target, getter, value));
    }

    private List<BranchModel> branchesForReturn(
            MethodModel method,
            ReturnModel bodyReturn) {

        // Only boolean-returning methods have branchy boolean expressions.
        // For any other return type (long, String, BigDecimal, ...), the
        // true/false decomposition doesn't apply.
        String returnType = method.returnType();
        if (!"boolean".equals(returnType) && !"Boolean".equals(returnType)) {
            return List.of();
        }

        List<BranchModel> branches = new ArrayList<>();

        ExprModel expr;
        try {
            expr = exprParser.parse(bodyReturn.expression());
        } catch (Exception e) {
            return branches;
        }

        List<SetupOutcome> pairs = decompose(expr);

        for (SetupOutcome pair : pairs) {
            branches.add(new BranchModel(
                    method.name(),
                    method.name() + "_" + pair.displayName(),
                    pair.setups(),
                    new BranchOutcome(
                            BranchOutcomeKind.RETURN,
                            pair.expectedValue(),
                            List.of()
                    )
            ));
        }

        return branches;
    }

    private record SetupOutcome(
            List<BranchSetup> setups,
            String expectedValue,
            String displayName
    ) {}

    /**
     * Decompose a boolean return expression into a set of (setup, outcome)
     * pairs. Each pair is one test scenario.
     */
    private List<SetupOutcome> decompose(ExprModel expr) {

        List<SetupOutcome> pairs = new ArrayList<>();

        if (expr instanceof ExprModel.And and) {
            // A && B:
            //   - A false -> false
            //   - A true, B true -> true
            //   - A true, B false -> false
            List<BranchSetup> aFalse = stubResolver.resolve(and.left(), false);
            if (aFalse != null) {
                pairs.add(new SetupOutcome(
                        aFalse,
                        "false",
                        "should_return_false_when_left_conjunct_is_false"
                ));
            }

            List<BranchSetup> aTrue = stubResolver.resolve(and.left(), true);
            List<BranchSetup> bTrue = stubResolver.resolve(and.right(), true);
            if (aTrue != null && bTrue != null) {
                List<BranchSetup> both = new ArrayList<>(aTrue);
                both.addAll(bTrue);
                pairs.add(new SetupOutcome(
                        both,
                        "true",
                        "should_return_true_when_both_conjuncts_hold"
                ));
            }

            List<BranchSetup> bFalse = stubResolver.resolve(and.right(), false);
            if (aTrue != null && bFalse != null) {
                List<BranchSetup> both = new ArrayList<>(aTrue);
                both.addAll(bFalse);
                pairs.add(new SetupOutcome(
                        both,
                        "false",
                        "should_return_false_when_right_conjunct_is_false"
                ));
            }

            return pairs;
        }

        if (expr instanceof ExprModel.Or or) {
            // A || B:
            //   - A true -> true
            //   - A false, B true -> true
            //   - A false, B false -> false
            List<BranchSetup> aTrue = stubResolver.resolve(or.left(), true);
            if (aTrue != null) {
                pairs.add(new SetupOutcome(
                        aTrue,
                        "true",
                        "should_return_true_when_left_disjunct_is_true"
                ));
            }

            List<BranchSetup> aFalse = stubResolver.resolve(or.left(), false);
            List<BranchSetup> bTrue = stubResolver.resolve(or.right(), true);
            if (aFalse != null && bTrue != null) {
                List<BranchSetup> both = new ArrayList<>(aFalse);
                both.addAll(bTrue);
                pairs.add(new SetupOutcome(
                        both,
                        "true",
                        "should_return_true_when_right_disjunct_is_true"
                ));
            }

            List<BranchSetup> bFalse = stubResolver.resolve(or.right(), false);
            if (aFalse != null && bFalse != null) {
                List<BranchSetup> both = new ArrayList<>(aFalse);
                both.addAll(bFalse);
                pairs.add(new SetupOutcome(
                        both,
                        "false",
                        "should_return_false_when_both_disjuncts_are_false"
                ));
            }

            return pairs;
        }

        // Single condition: true and false.
        List<BranchSetup> trueSetup = stubResolver.resolve(expr, true);
        if (trueSetup != null) {
            pairs.add(new SetupOutcome(trueSetup, "true", "should_return_true"));
        }
        List<BranchSetup> falseSetup = stubResolver.resolve(expr, false);
        if (falseSetup != null) {
            pairs.add(new SetupOutcome(falseSetup, "false", "should_return_false"));
        }

        return pairs;
    }

    private String shortName(String expr) {
        String s = expr.replaceAll("[^A-Za-z0-9]+", "_")
                .replaceAll("^_|_$", "");
        return s.length() > 30 ? s.substring(0, 30) : s;
    }
}