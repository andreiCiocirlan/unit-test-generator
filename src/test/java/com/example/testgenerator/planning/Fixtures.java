package com.example.testgenerator.planning;

import com.example.testgenerator.analysis.DtoAnalyzer;
import com.example.testgenerator.analysis.model.*;

import java.util.List;

public final class Fixtures {

    private Fixtures() {}

    public static MethodCallModel call(
            String target,
            String methodName,
            CallKind kind,
            String... args) {
        return new MethodCallModel(
                target,
                "",
                target,
                methodName,
                List.of(args),
                kind,
                StatementContext.topLevel()
        );
    }

    public static MethodCallModel call(
            String target,
            String methodName,
            CallKind kind,
            StatementContext ctx,
            String... args) {
        return new MethodCallModel(
                target,
                "",
                target,
                methodName,
                List.of(args),
                kind,
                ctx
        );
    }

    public static MethodModel method(
            String name,
            String returnType,
            List<ParameterModel> parameters,
            List<MethodCallModel> calls) {
        return method(name, returnType, parameters, calls, List.of(), List.of(), List.of());
    }

    public static MethodModel method(
            String name,
            String returnType,
            List<ParameterModel> parameters,
            List<MethodCallModel> calls,
            List<AssignmentModel> assignments,
            List<ReturnModel> returns,
            List<ConditionModel> conditions) {
        return new MethodModel(
                name,
                returnType,
                List.of(),
                List.of(),
                parameters,
                calls,
                conditions,
                List.of(),
                returns,
                assignments,
                List.of()
        );
    }

    public static AssignmentModel assign(
            String name,
            String type,
            String expression) {
        return new AssignmentModel(
                name,
                type,
                expression,
                StatementContext.topLevel()
        );
    }

    public static ReturnModel ret(String expression) {
        return new ReturnModel(expression, StatementContext.topLevel());
    }

    public static TestDataAssembler testDataAssembler() {
        DtoAnalyzer dtoAnalyzer = new DtoAnalyzer();
        DefaultValueResolver resolver = new DefaultValueResolver(dtoAnalyzer);
        resolver.configure(
                java.nio.file.Path.of("/nonexistent"),
                java.util.List.of()
        );
        return new TestDataAssembler(resolver);
    }

    public static MethodModel methodWithConditions(
            String name,
            String returnType,
            List<ParameterModel> parameters,
            List<ConditionModel> conditions) {
        return method(
                name,
                returnType,
                parameters,
                List.of(),
                List.of(),
                List.of(),
                conditions
        );
    }
}