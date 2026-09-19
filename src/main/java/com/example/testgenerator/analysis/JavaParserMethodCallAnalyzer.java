package com.example.testgenerator.analysis;

import com.example.testgenerator.analysis.model.CallKind;
import com.example.testgenerator.analysis.model.DependencyModel;
import com.example.testgenerator.analysis.model.MethodCallModel;
import com.github.javaparser.ast.expr.MethodCallExpr;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JavaParserMethodCallAnalyzer
        implements MethodCallAnalyzer {

    @Override
    public MethodCallModel analyze(
            MethodCallExpr methodCall,
            List<DependencyModel> dependencies) {

        String target = methodCall.getScope()
                .map(Object::toString)
                .map(this::normalizeTarget)
                .orElse("");

        CallKind kind = dependencies.stream()
                .anyMatch(dependency ->
                        dependency.name().equals(target))
                ? CallKind.DEPENDENCY
                : CallKind.UNKNOWN;

        List<String> arguments =
                methodCall.getArguments()
                        .stream()
                        .map(Object::toString)
                        .toList();

        return new MethodCallModel(
                target,
                methodCall.getNameAsString(),
                arguments,
                kind
        );
    }

    private String normalizeTarget(String target) {

        if (target.startsWith("this.")) {
            return target.substring("this.".length());
        }

        return target;
    }
}