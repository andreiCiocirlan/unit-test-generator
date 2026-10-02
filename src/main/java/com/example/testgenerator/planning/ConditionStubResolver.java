package com.example.testgenerator.planning;

import com.example.testgenerator.analysis.ExpressionParser;
import com.example.testgenerator.planning.model.BranchSetup;
import com.example.testgenerator.analysis.model.ExprModel;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Given a condition expression and a desired truth value, produce the
 * mock setups that make the condition evaluate to that value, or return
 * null if we don't know how.
 */
public class ConditionStubResolver {

    private final ExpressionParser exprParser = new ExpressionParser();

    private static final String BIG_DECIMAL_ONE = "java.math.BigDecimal.ONE";
    private static final String BIG_DECIMAL_ZERO = "java.math.BigDecimal.ZERO";
    private static final String BIG_DECIMAL_MINUS_ONE = "java.math.BigDecimal.valueOf(-1)";

    /**
     * Matches a simple Java identifier (e.g. {@code invoice}), used to
     * determine whether a receiver is a bare variable name rather than
     * a chained call like {@code invoice.getX()}.
     */
    private static final Pattern SIMPLE_IDENTIFIER_PATTERN =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    public List<BranchSetup> resolve(ExprModel expr, boolean truthValue) {

        if (expr instanceof ExprModel.Not not) {
            return resolve(not.inner(), !truthValue);
        }

        if (expr instanceof ExprModel.And and) {
            if (!truthValue) {
                // To make A && B false, either one false is enough.
                // Prefer the left conjunct.
                List<BranchSetup> left = resolve(and.left(), false);
                if (left != null) return left;
                return resolve(and.right(), false);
            }
            // To make A && B true, both must be true.
            List<BranchSetup> left = resolve(and.left(), true);
            List<BranchSetup> right = resolve(and.right(), true);
            if (left == null || right == null) return null;
            List<BranchSetup> both = new ArrayList<>(left);
            both.addAll(right);
            return both;
        }

        if (expr instanceof ExprModel.Or or) {
            if (truthValue) {
                List<BranchSetup> left = resolve(or.left(), true);
                if (left != null) return left;
                return resolve(or.right(), true);
            }
            List<BranchSetup> left = resolve(or.left(), false);
            List<BranchSetup> right = resolve(or.right(), false);
            if (left == null || right == null) return null;
            List<BranchSetup> both = new ArrayList<>(left);
            both.addAll(right);
            return both;
        }

        if (expr instanceof ExprModel.Comparison cmp) {
            return resolveComparison(cmp, truthValue);
        }

        if (expr instanceof ExprModel.Call call) {

            // "LITERAL".equals(x.getY())
            if (call.method().equals("equals")
                    && call.receiver().startsWith("\"")
                    && call.args().size() == 1) {

                String literal = call.receiver()
                        .substring(1, call.receiver().length() - 1);

                ExprModel arg;
                try {
                    arg = exprParser.parse(call.args().get(0));
                } catch (Exception e) {
                    return null;
                }

                if (arg instanceof ExprModel.Call inner) {
                    String target = simpleReceiver(inner.receiver());
                    if (target != null && inner.args().isEmpty()) {
                        return List.of(new BranchSetup(
                                target,
                                inner.method(),
                                truthValue
                                        ? "\"" + literal + "\""
                                        : "\"__other__\""
                        ));
                    }
                }
                return null;
            }

            // A bare boolean call like `invoice.isOverdue()`.
            if (call.args().isEmpty()) {
                String receiver = simpleReceiver(call.receiver());
                if (receiver != null) {
                    return List.of(new BranchSetup(
                            receiver,
                            call.method(),
                            truthValue ? "true" : "false"
                    ));
                }
            }

            return null;
        }

        return null;
    }

    private List<BranchSetup> resolveComparison(
            ExprModel.Comparison cmp,
            boolean truthValue) {

        // x.getY() == null  or  x.getY() != null
        if (cmp.operator().equals("==") || cmp.operator().equals("!=")) {
            boolean leftIsCall = cmp.left() instanceof ExprModel.Call;
            ExprModel callSide = leftIsCall ? cmp.left() : cmp.right();
            ExprModel other = leftIsCall ? cmp.right() : cmp.left();

            if (!(callSide instanceof ExprModel.Call c)) return null;
            if (!(other instanceof ExprModel.Other o)) return null;
            if (!o.source().equals("null")) return null;

            String receiver = simpleReceiver(c.receiver());
            if (receiver == null) return null;

            boolean wantNull = (cmp.operator().equals("==")) == truthValue;
            return List.of(new BranchSetup(
                    receiver,
                    c.method(),
                    wantNull ? "null" : BIG_DECIMAL_ONE
            ));
        }

        // x.getY().compareTo(CONST) OP LITERAL
        if (cmp.left() instanceof ExprModel.Call call
            && call.method().equals("compareTo")) {

            ExprModel receiver = parseReceiver(call.receiver());
            if (!(receiver instanceof ExprModel.Call inner)) return null;

            String target = simpleReceiver(inner.receiver());
            if (target == null) return null;

            String op = cmp.operator();
            String value = numericValueFor(op, truthValue);
            if (value == null) return null;

            List<BranchSetup> setups = new ArrayList<>();
            setups.add(new BranchSetup(target, inner.method(), value));

            // Also stub the argument to compareTo if it's a getter on a
            // mock. E.g. properties.getMinimumInvoiceAmount().
            if (call.args().size() == 1) {
                ExprModel argExpr;
                try {
                    argExpr = exprParser.parse(call.args().get(0));
                } catch (Exception e) {
                    argExpr = null;
                }
                if (argExpr instanceof ExprModel.Call argCall) {
                    String argTarget = simpleReceiver(argCall.receiver());
                    if (argTarget != null && argCall.args().isEmpty()) {
                        // Stub the argument's getter to a fixed baseline value.
                        // We don't know its type, so use BigDecimal.ZERO as a
                        // type-neutral default for numeric comparisons.
                        setups.add(new BranchSetup(
                                argTarget,
                                argCall.method(),
                                BIG_DECIMAL_ZERO
                        ));
                    }
                }
            }

            return setups;
        }

        return null;
    }

    private String numericValueFor(String op, boolean truthValue) {
        return switch (op) {
            case ">"  -> truthValue ? BIG_DECIMAL_ONE
                    : BIG_DECIMAL_ZERO;
            case ">=" -> truthValue ? BIG_DECIMAL_ZERO
                    : BIG_DECIMAL_MINUS_ONE;
            case "<"  -> truthValue ? BIG_DECIMAL_MINUS_ONE
                    : BIG_DECIMAL_ONE;
            case "<=" -> truthValue ? BIG_DECIMAL_ZERO
                    : BIG_DECIMAL_ONE;
            default -> null;
        };
    }

    /** "invoice" -> "invoice"; "invoice.getX()" -> null */
    private String simpleReceiver(String receiver) {
        if (receiver == null || receiver.isBlank()) return null;
        if (SIMPLE_IDENTIFIER_PATTERN.matcher(receiver).matches()) return receiver;
        return null;
    }

    private ExprModel parseReceiver(String receiver) {
        try {
            return exprParser.parse(receiver);
        } catch (Exception e) {
            return new ExprModel.Other(receiver);
        }
    }
}