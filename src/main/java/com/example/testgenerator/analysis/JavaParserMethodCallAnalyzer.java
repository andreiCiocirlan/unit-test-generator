package com.example.testgenerator.analysis;

import com.example.testgenerator.analysis.model.CallKind;
import com.example.testgenerator.analysis.model.DependencyModel;
import com.example.testgenerator.analysis.model.MethodCallModel;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JavaParserMethodCallAnalyzer
        implements MethodCallAnalyzer {

    private final StatementContextResolver contextResolver;

    public JavaParserMethodCallAnalyzer(
            StatementContextResolver contextResolver) {
        this.contextResolver = contextResolver;
    }

    @Override
    public MethodCallModel analyze(
            MethodCallExpr methodCall,
            List<DependencyModel> dependencies) {

        Expression scope = methodCall.getScope().orElse(null);

        String target = immediateReceiverName(scope);

        String receiverChain = scope == null
                ? ""
                : scope.toString();

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
                kind,
                contextResolver.resolve(methodCall)
        );
    }

    private String immediateReceiverName(Expression scope) {
        if (scope == null) return "";

        if (scope.isNameExpr()) {
            return scope.asNameExpr().getNameAsString();
        }

        if (scope.isThisExpr()) {
            return "";
        }

        if (scope.isFieldAccessExpr()) {
            var fa = scope.asFieldAccessExpr();
            if (fa.getScope().isThisExpr()) {
                return fa.getNameAsString();
            }
            return "";
        }

        return "";
    }

    private CallKind classify(
            Expression scope,
            List<DependencyModel> dependencies) {

        if (scope == null) {
            return CallKind.INTERNAL;
        }

        if (scope.isThisExpr()) {
            return CallKind.INTERNAL;
        }

        if (scope.isFieldAccessExpr()
            && scope.asFieldAccessExpr().getScope().isThisExpr()) {

            String field = scope.asFieldAccessExpr().getNameAsString();
            return DependencyModel.findByName(dependencies, field).isPresent()
                    ? CallKind.DEPENDENCY
                    : CallKind.INTERNAL;
        }

        if (scope.isFieldAccessExpr()) {
            return CallKind.CHAINED;
        }

        if (scope.isMethodCallExpr()
            || scope.isObjectCreationExpr()
            || scope.isCastExpr()) {
            return CallKind.CHAINED;
        }

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
        return Character.isUpperCase(name.charAt(0));
    }
}