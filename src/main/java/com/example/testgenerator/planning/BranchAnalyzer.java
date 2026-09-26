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

        // 1. Early-return guards: `if (x == null) return <expr>;`
        for (ConditionModel condition : method.conditions()) {
            ReturnModel ret = findReturnInCondition(method, condition);
            if (ret == null) continue;
            if (!isSimpleNullCheck(condition.expression())) continue;

            branches.add(new BranchModel(
                    method.name(),
                    method.name() + "_should_return_when_"
                            + shortName(condition.expression()),
                    List.of(),
                    new BranchOutcome(BranchOutcomeKind.RETURN, ret.expression(), List.of())
            ));
        }

        // 2. Body-return branches: the last return, if top-level.
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

    private boolean isSimpleNullCheck(String expr) {
        return expr.matches("[A-Za-z_][A-Za-z0-9_]*\\s*==\\s*null");
    }

    private ReturnModel findReturnInCondition(
            MethodModel method,
            ConditionModel condition) {

        for (ReturnModel r : method.returns()) {
            if (r.context() == null) continue;
            for (String ifCond : r.context().ifConditions()) {
                if (ifCond.equals(condition.expression())) return r;
            }
        }
        return null;
    }

    private String shortName(String expr) {
        String s = expr.replaceAll("[^A-Za-z0-9]+", "_")
                .replaceAll("^_|_$", "");
        return s.length() > 30 ? s.substring(0, 30) : s;
    }
}