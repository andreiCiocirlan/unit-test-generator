package com.example.testgenerator.analysis.model;

import java.util.List;

/**
 * A parsed Java expression, simplified to the node kinds we care about.
 */
public sealed interface ExprModel {

    String source();     // the original text, for display and fallback

    record And(ExprModel left, ExprModel right, String source) implements ExprModel {}
    record Or(ExprModel left, ExprModel right, String source) implements ExprModel {}
    record Not(ExprModel inner, String source) implements ExprModel {}

    /** x.method() */
    record Call(String receiver, String method, List<String> args, String source)
            implements ExprModel {}

    /** "LITERAL" */
    record StringLiteral(String value, String source) implements ExprModel {}

    /** 0, 1L, BigDecimal.ZERO — anything not a string or call */
    record Other(String source) implements ExprModel {}

    /** x == null, x != null, a > b, etc. */
    record Comparison(String operator, ExprModel left, ExprModel right, String source)
            implements ExprModel {}
}