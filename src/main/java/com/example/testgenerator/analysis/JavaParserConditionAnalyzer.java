package com.example.testgenerator.analysis;

import com.example.testgenerator.analysis.model.ConditionModel;
import com.example.testgenerator.analysis.model.MethodCallModel;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JavaParserConditionAnalyzer implements ConditionAnalyzer {

    @Override
    public ConditionModel analyze(IfStmt ifStmt) {

        String expression =
                ifStmt.getCondition().toString();

        List<MethodCallModel> methodCalls =
                ifStmt.findAll(MethodCallExpr.class)
                        .stream()
                        .map(this::toMethodCallModel)
                        .toList();

        List<String> thrownExceptions =
                ifStmt.findAll(ThrowStmt.class)
                        .stream()
                        .map(this::extractExceptionType)
                        .toList();

        return new ConditionModel(
                expression,
                methodCalls,
                thrownExceptions
        );
    }

    private MethodCallModel toMethodCallModel(
            MethodCallExpr methodCall) {

        String target = methodCall.getScope()
                .map(Object::toString)
                .orElse("");

        List<String> arguments =
                methodCall.getArguments()
                        .stream()
                        .map(Object::toString)
                        .toList();

        return new MethodCallModel(
                target,
                methodCall.getNameAsString(),
                arguments,
                null
        );
    }

    private String extractExceptionType(
            ThrowStmt throwStmt) {

        if (throwStmt.getExpression()
                .isObjectCreationExpr()) {

            return throwStmt.getExpression()
                    .asObjectCreationExpr()
                    .getType()
                    .asString();
        }

        return throwStmt.getExpression()
                .toString();
    }
}