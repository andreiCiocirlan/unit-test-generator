package com.example.testgenerator.analysis;

import com.example.testgenerator.analysis.model.*;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class JavaParserAnalyzer implements com.example.testgenerator.analysis.model.JavaAnalyzer {

    private final SpringTypeClassifier springTypeClassifier;
    private final ConstructorResolver constructorResolver;
    private final MethodCallAnalyzer methodCallAnalyzer;
    private final ConditionAnalyzer conditionAnalyzer;
    private final StatementContextResolver contextResolver = new StatementContextResolver();

    public JavaParserAnalyzer(
            SpringTypeClassifier springTypeClassifier,
            ConstructorResolver constructorResolver,
            MethodCallAnalyzer methodCallAnalyzer,
            ConditionAnalyzer conditionAnalyzer) {

        this.springTypeClassifier = springTypeClassifier;
        this.constructorResolver = constructorResolver;
        this.methodCallAnalyzer = methodCallAnalyzer;
        this.conditionAnalyzer = conditionAnalyzer;
    }

    @Override
    public ClassModel analyze(Path sourceFile) {

        CompilationUnit compilationUnit = parse(sourceFile);

        ClassOrInterfaceDeclaration classDeclaration =
                findMainClass(compilationUnit);

        List<String> annotations =
                extractAnnotations(classDeclaration);

        List<DependencyModel> dependencies =
                extractDependencies(classDeclaration);

        return new ClassModel(
                compilationUnit.getPackageDeclaration()
                        .map(packageDeclaration -> packageDeclaration.getNameAsString())
                        .orElse(""),
                classDeclaration.getNameAsString(),
                annotations,
                dependencies,
                extractMethods(classDeclaration, dependencies),
                springTypeClassifier.classify(annotations),
                compilationUnit.getImports().stream()
                        .map(importDeclaration -> importDeclaration.isAsterisk()
                                ? importDeclaration.getNameAsString() + ".*"
                                : importDeclaration.getNameAsString())
                        .toList()
        );
    }

    private CompilationUnit parse(Path sourceFile) {
        try {
            StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
            return StaticJavaParser.parse(sourceFile);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Unable to parse source file: " + sourceFile,
                    e
            );
        }
    }

    private ClassOrInterfaceDeclaration findMainClass(
            CompilationUnit compilationUnit) {

        return compilationUnit
                .findFirst(ClassOrInterfaceDeclaration.class)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "No class found in source file"
                        ));
    }

    private List<String> extractAnnotations(
            ClassOrInterfaceDeclaration classDeclaration) {

        return classDeclaration
                .getAnnotations()
                .stream()
                .map(annotation ->
                        annotation.getNameAsString())
                .toList();
    }

    private List<DependencyModel> extractDependencies(
            ClassOrInterfaceDeclaration classDeclaration) {

        List<DependencyModel> dependencies = new ArrayList<>();

        addFinalFieldDependencies(classDeclaration, dependencies);

        if (dependencies.isEmpty()) {
            constructorResolver
                    .resolve(classDeclaration)
                    .ifPresent(constructor ->
                            addConstructorDependencies(constructor, dependencies));
        }

        return dependencies;
    }

    private void addFinalFieldDependencies(
            ClassOrInterfaceDeclaration classDeclaration,
            List<DependencyModel> dependencies) {

        for (FieldDeclaration field : classDeclaration.getFields()) {

            boolean isPrivate = field.hasModifier(Modifier.Keyword.PRIVATE);
            boolean isFinal   = field.hasModifier(Modifier.Keyword.FINAL);

            if (!isPrivate || !isFinal) {
                continue;
            }

            // Skip static fields (e.g. constants) — they aren't injected
            if (field.hasModifier(Modifier.Keyword.STATIC)) {
                continue;
            }

            for (VariableDeclarator variable : field.getVariables()) {

                dependencies.add(
                        new DependencyModel(
                                variable.getTypeAsString(),
                                variable.getNameAsString(),
                                DependencyKind.MOCK
                        )
                );
            }
        }
    }

    private void addConstructorDependencies(
            ConstructorDeclaration constructor,
            List<DependencyModel> dependencies) {

        for (Parameter parameter : constructor.getParameters()) {

            dependencies.add(
                    new DependencyModel(
                            parameter.getTypeAsString(),
                            parameter.getNameAsString(),
                            DependencyKind.MOCK
                    )
            );
        }
    }

    private List<MethodModel> extractMethods(
            ClassOrInterfaceDeclaration classDeclaration,
            List<DependencyModel> dependencies) {

        return classDeclaration
                .getMethods()
                .stream()
                .map(method ->
                        toMethodModel(method, dependencies))
                .toList();
    }

    private MethodModel toMethodModel(
            MethodDeclaration method,
            List<DependencyModel> dependencies) {

        List<String> annotations = method.getAnnotations()
                .stream()
                .map(a -> a.getNameAsString())
                .toList();

        List<String> declaredThrows = method.getThrownExceptions()
                .stream()
                .map(Object::toString)
                .toList();

        List<ParameterModel> parameters = method.getParameters()
                .stream()
                .map(p -> new ParameterModel(
                        p.getTypeAsString(),
                        p.getNameAsString()))
                .toList();

        List<MethodCallModel> methodCalls = extractMethodCalls(
                method, dependencies);

        List<ConditionModel> conditions = extractConditions(
                method, dependencies);

        List<TryModel> tries = extractTries(
                method, dependencies);

        List<ReturnModel> returns = extractReturns(method);

        List<AssignmentModel> assignments = extractAssignments(method);

        List<ThrowModel> throwsStatements = extractThrows(method);

        return new MethodModel(
                method.getNameAsString(),
                method.getTypeAsString(),
                annotations,
                declaredThrows,
                parameters,
                methodCalls,
                conditions,
                tries,
                returns,
                assignments,
                throwsStatements
        );
    }

    private List<MethodCallModel> extractMethodCalls(
            MethodDeclaration method,
            List<DependencyModel> dependencies) {

        return method.findAll(MethodCallExpr.class)
                .stream()
                .map(call -> methodCallAnalyzer.analyze(call, dependencies))
                .toList();
    }

    private List<ConditionModel> extractConditions(
            MethodDeclaration method,
            List<DependencyModel> dependencies) {

        return method.findAll(IfStmt.class)
                .stream()
                .map(condition ->
                        conditionAnalyzer.analyze(condition, dependencies))
                .toList();
    }

    private List<ReturnModel> extractReturns(MethodDeclaration method) {
        return method.findAll(ReturnStmt.class)
                .stream()
                .map(returnStmt -> new ReturnModel(
                        returnStmt.getExpression()
                                .map(Object::toString)
                                .orElse(""),
                        contextResolver.resolve(returnStmt)
                ))
                .toList();
    }

    private List<ThrowModel> extractThrows(MethodDeclaration method) {
        return method.findAll(ThrowStmt.class)
                .stream()
                .map(throwStmt -> new ThrowModel(
                        exceptionTypeOf(throwStmt),
                        throwStmt.getExpression().toString(),
                        contextResolver.resolve(throwStmt)
                ))
                .toList();
    }

    private String exceptionTypeOf(ThrowStmt throwStmt) {
        if (throwStmt.getExpression().isObjectCreationExpr()) {
            return throwStmt.getExpression()
                    .asObjectCreationExpr()
                    .getType()
                    .asString();
        }
        return "";
    }

    private List<AssignmentModel> extractAssignments(MethodDeclaration method) {
        return method.findAll(VariableDeclarationExpr.class)
                .stream()
                .filter(vd -> !isForInitializer(vd))
                .flatMap(variableDeclaration ->
                        variableDeclaration.getVariables().stream()
                                .map(variable -> new AssignmentModel(
                                        variable.getNameAsString(),
                                        variable.getTypeAsString(),
                                        variable.getInitializer()
                                                .map(Object::toString)
                                                .orElse(""),
                                        contextResolver.resolve(variable)
                                ))
                )
                .toList();
    }

    private boolean isForInitializer(VariableDeclarationExpr vd) {
        return vd.getParentNode()
                .filter(p -> p instanceof ForStmt)
                .map(p -> ((ForStmt) p).getInitialization().contains(vd))
                .orElse(false);
    }

    private List<TryModel> extractTries(
            MethodDeclaration method,
            List<DependencyModel> dependencies) {

        return method.findAll(TryStmt.class)
                .stream()
                .map(tryStmt -> toTryModel(tryStmt, dependencies))
                .toList();
    }

    private TryModel toTryModel(
            TryStmt tryStmt,
            List<DependencyModel> dependencies) {

        var body = tryStmt.getTryBlock();

        List<MethodCallModel> bodyCalls = body
                .findAll(MethodCallExpr.class).stream()
                .map(call -> methodCallAnalyzer.analyze(call, dependencies))
                .toList();

        List<ReturnModel> bodyReturns = body
                .findAll(ReturnStmt.class).stream()
                .map(r -> new ReturnModel(
                        r.getExpression().map(Object::toString).orElse(""),
                        contextResolver.resolve(r)))
                .toList();

        List<ThrowModel> bodyThrows = body
                .findAll(ThrowStmt.class).stream()
                .map(t -> new ThrowModel(
                        exceptionTypeOf(t),
                        t.getExpression().toString(),
                        contextResolver.resolve(t)))
                .toList();

        List<CatchModel> catches = tryStmt.getCatchClauses()
                .stream()
                .map(catchClause -> toCatchModel(catchClause, dependencies))
                .toList();

        return new TryModel(bodyCalls, bodyReturns, bodyThrows, catches);
    }

    private CatchModel toCatchModel(
            CatchClause catchClause,
            List<DependencyModel> dependencies) {

        var body = catchClause.getBody();

        List<MethodCallModel> calls = body
                .findAll(MethodCallExpr.class).stream()
                .map(call -> methodCallAnalyzer.analyze(call, dependencies))
                .toList();

        List<ReturnModel> returns = body
                .findAll(ReturnStmt.class).stream()
                .map(r -> new ReturnModel(
                        r.getExpression().map(Object::toString).orElse(""),
                        contextResolver.resolve(r)))
                .toList();

        List<ThrowModel> throwsStatements = body
                .findAll(ThrowStmt.class).stream()
                .map(t -> new ThrowModel(
                        exceptionTypeOf(t),
                        t.getExpression().toString(),
                        contextResolver.resolve(t)))
                .toList();

        return new CatchModel(
                catchClause.getParameter().getType().asString(),
                catchClause.getParameter().getNameAsString(),
                calls,
                returns,
                throwsStatements
        );
    }


}