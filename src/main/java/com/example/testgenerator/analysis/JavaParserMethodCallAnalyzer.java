package com.example.testgenerator.analysis;

import com.example.testgenerator.analysis.model.CallKind;
import com.example.testgenerator.analysis.model.DependencyModel;
import com.example.testgenerator.analysis.model.MethodCallModel;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class JavaParserMethodCallAnalyzer
        implements MethodCallAnalyzer {

    @Override
    public MethodCallModel analyze(
            MethodCallExpr methodCall,
            List<DependencyModel> dependencies) {

        Expression scope = methodCall.getScope().orElse(null);

        // The receiver that the *immediate* call is invoked on.
        // "" when the receiver is not a plain identifier (e.g. a chained call,
        // object creation, cast, or an unqualified `this`-less call).
        String target = immediateReceiverName(scope);

        // Full source text of the receiver chain, for diagnostics.
        // "" when the call is unqualified.
        String receiverChain = scope == null
                ? ""
                : scope.toString();

        // Only resolve a type when the target is a real identifier.
        // For chained calls (target == "") there is no meaningful dependency type.
        String targetType = target.isEmpty()
                ? ""
                : DependencyModel.findByName(dependencies, target)
                .map(DependencyModel::type)
                .orElse("");

        CallKind kind = classify(scope, dependencies);

        List<String> arguments = methodCall.getArguments()
                .stream()
                .map(Expression::toString)
                .toList();

        return new MethodCallModel(
                target,
                targetType,
                receiverChain,
                methodCall.getNameAsString(),
                arguments,
                kind
        );
    }

    /**
     * The name of the immediate receiver, only when it is a plain identifier
     * (NameExpr) or a this-qualified field. Returns "" when the receiver is
     * itself a method call, object creation, cast, etc.
     */
    private String immediateReceiverName(Expression scope) {
        if (scope == null) return "";

        if (scope.isNameExpr()) {
            return scope.asNameExpr().getNameAsString();
        }

        if (scope.isThisExpr()) {
            return "";  // unqualified this
        }

        if (scope.isFieldAccessExpr()) {
            var fa = scope.asFieldAccessExpr();
            if (fa.getScope().isThisExpr()) {
                return fa.getNameAsString();
            }
            // x.y.z — the immediate receiver is `x.y`, not a plain name
            return "";
        }

        // Anything else (method call, object creation, cast, lambda, ...)
        // is not a plain name.
        return "";
    }

    // ---------------------------------------------------------------
    // Receiver chain walking
    // ---------------------------------------------------------------

    /**
     * Walks down a receiver chain to find the root identifier.
     * Examples:
     *   paymentClient                -> "paymentClient"
     *   this.paymentClient           -> "paymentClient"
     *   order.getUser().getEmail()   -> "order"
     *   this.helper().doWork()       -> ""   (this-qualified internal)
     *   Foo.bar()                    -> "Foo" (static-ish)
     *   null scope (unqualified)     -> ""
     */
    private String rootName(Expression scope) {

        if (scope == null) {
            return "";
        }

        if (scope.isNameExpr()) {
            return scope.asNameExpr().getNameAsString();
        }

        if (scope.isThisExpr()) {
            return "";
        }

        if (scope.isFieldAccessExpr()) {
            // e.g. this.paymentClient -> scope is "this.paymentClient",
            // its "scope" is `this`, name is "paymentClient"
            var fieldAccess = scope.asFieldAccessExpr();
            if (fieldAccess.getScope().isThisExpr()) {
                return fieldAccess.getNameAsString();
            }
            return rootName(fieldAccess.getScope());
        }

        if (scope.isMethodCallExpr()) {
            // e.g. order.getUser() -> recurse into its scope
            return scope.asMethodCallExpr()
                    .getScope()
                    .map(this::rootName)
                    .orElse("");
        }

        if (scope.isObjectCreationExpr()) {
            // new Foo().bar() -> no stable root; treat as unknown
            return "";
        }

        return scope.toString();
    }

    private CallKind classify(
            Expression scope,
            List<DependencyModel> dependencies) {

        // Unqualified: foo() -> internal
        if (scope == null) {
            return CallKind.INTERNAL;
        }

        // this.foo()
        if (scope.isThisExpr()) {
            return CallKind.INTERNAL;
        }

        // this.field.foo() -> immediate receiver is `field`
        if (scope.isFieldAccessExpr()
            && scope.asFieldAccessExpr().getScope().isThisExpr()) {

            String field = scope.asFieldAccessExpr().getNameAsString();
            return DependencyModel.findByName(dependencies, field).isPresent()
                    ? CallKind.DEPENDENCY
                    : CallKind.INTERNAL;
        }

        // Any other field access: x.y.foo() — receiver is `x.y`, not a plain name
        if (scope.isFieldAccessExpr()) {
            return CallKind.CHAINED;
        }

        // Receiver is itself a call / object creation / cast
        // e.g. userRepository.findById(id).orElseThrow()
        if (scope.isMethodCallExpr()
            || scope.isObjectCreationExpr()
            || scope.isCastExpr()) {
            return CallKind.CHAINED;
        }

        // Plain identifier
        if (scope.isNameExpr()) {
            String name = scope.asNameExpr().getNameAsString();

            if (DependencyModel.findByName(dependencies, name).isPresent()) {
                return CallKind.DEPENDENCY;
            }
            if (isLikelyClassName(name)) {
                return CallKind.STATIC;
            }
            return CallKind.LOCAL;
        }

        return CallKind.UNKNOWN;
    }

    private boolean isLikelyClassName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        char c = name.charAt(0);
        return Character.isUpperCase(c);
    }

    // ---------------------------------------------------------------
    // Utility for later use (avoids duplicate work in callers)
    // ---------------------------------------------------------------

    public Set<String> dependencyNames(List<DependencyModel> dependencies) {
        Set<String> names = new HashSet<>();
        for (DependencyModel d : dependencies) {
            names.add(d.name());
        }
        return names;
    }
}