package com.example.testgenerator.analysis.model;

public enum CallKind {
    DEPENDENCY,   // receiver is an injected dependency field/local
    INTERNAL,     // unqualified or this.-qualified
    STATIC,       // likely class-name receiver
    LOCAL,        // local variable or parameter
    CHAINED,      // receiver is the *result* of another call / expression
    UNKNOWN
}