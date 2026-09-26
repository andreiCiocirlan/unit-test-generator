package com.example.testgenerator.analysis;

import com.example.testgenerator.analysis.model.ExprModel;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.expr.Expression;

public class ExpressionParser {

    public ExprModel parse(String expressionText) {
        var expr = StaticJavaParser.parseExpression(expressionText);
        return translate(expr);
    }

    private ExprModel translate(Expression e) {

        if (e.isBinaryExpr()) {
            var b = e.asBinaryExpr();
            String op = b.getOperator().asString();

            if (op.equals("&&")) {
                return new ExprModel.And(
                        translate(b.getLeft()),
                        translate(b.getRight()),
                        e.toString()
                );
            }
            if (op.equals("||")) {
                return new ExprModel.Or(
                        translate(b.getLeft()),
                        translate(b.getRight()),
                        e.toString()
                );
            }
            // ==, !=, <, <=, >, >=
            return new ExprModel.Comparison(
                    op,
                    translate(b.getLeft()),
                    translate(b.getRight()),
                    e.toString()
            );
        }

        if (e.isUnaryExpr()) {
            var u = e.asUnaryExpr();
            if (u.getOperator().asString().equals("!")) {
                return new ExprModel.Not(translate(u.getExpression()), e.toString());
            }
            return new ExprModel.Other(e.toString());
        }

        if (e.isMethodCallExpr()) {
            var m = e.asMethodCallExpr();
            String receiver = m.getScope().map(Object::toString).orElse("");
            return new ExprModel.Call(
                    receiver,
                    m.getNameAsString(),
                    m.getArguments().stream().map(Object::toString).toList(),
                    e.toString()
            );
        }

        if (e.isStringLiteralExpr()) {
            return new ExprModel.StringLiteral(
                    e.asStringLiteralExpr().asString(),
                    e.toString()
            );
        }

        if (e.isNullLiteralExpr()) {
            return new ExprModel.Other("null");
        }

        return new ExprModel.Other(e.toString());
    }
}