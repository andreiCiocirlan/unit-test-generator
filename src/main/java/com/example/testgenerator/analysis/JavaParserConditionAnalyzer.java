package com.example.testgenerator.analysis;

import com.example.testgenerator.analysis.model.ConditionModel;
import com.example.testgenerator.analysis.model.DependencyModel;
import com.example.testgenerator.analysis.model.MethodCallModel;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JavaParserConditionAnalyzer implements ConditionAnalyzer {

    private final MethodCallAnalyzer methodCallAnalyzer;
    private final StatementContextResolver contextResolver;

    public JavaParserConditionAnalyzer(
            MethodCallAnalyzer methodCallAnalyzer,
            StatementContextResolver contextResolver) {

        this.methodCallAnalyzer = methodCallAnalyzer;
        this.contextResolver = contextResolver;
    }

    @Override
    public ConditionModel analyze(
            IfStmt ifStmt,
            List<DependencyModel> dependencies) {

        String expression = ifStmt.getCondition().toString();

        List<MethodCallModel> methodCalls =
                ifStmt.findAll(MethodCallExpr.class)
                        .stream()
                        .map(call ->
                                methodCallAnalyzer.analyze(call, dependencies))
                        .toList();

        List<String> thrownExceptions =
                ifStmt.findAll(ThrowStmt.class)
                        .stream()
                        .map(this::extractExceptionType)
                        .toList();

        return new ConditionModel(
                expression,
                methodCalls,
                thrownExceptions,
                contextResolver.resolve(ifStmt)
        );
    }

    private String extractExceptionType(ThrowStmt throwStmt) {
        if (throwStmt.getExpression().isObjectCreationExpr()) {
            return throwStmt.getExpression()
                    .asObjectCreationExpr()
                    .getType()
                    .asString();
        }
        return throwStmt.getExpression().toString();
    }
}