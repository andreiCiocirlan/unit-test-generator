package com.example.testgenerator.analysis;

import com.example.testgenerator.analysis.model.*;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
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
                        .map(packageDeclaration ->
                                packageDeclaration.getNameAsString())
                        .orElse(""),

                classDeclaration.getNameAsString(),

                annotations,

                dependencies,

                extractMethods(
                        classDeclaration,
                        dependencies
                ),

                springTypeClassifier.classify(annotations)
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

        List<ParameterModel> parameters =
                method.getParameters()
                        .stream()
                        .map(parameter ->
                                new ParameterModel(
                                        parameter.getTypeAsString(),
                                        parameter.getNameAsString()
                                )
                        )
                        .toList();

        List<MethodCallModel> methodCalls =
                method.findAll(MethodCallExpr.class)
                        .stream()
                        .map(call ->
                                methodCallAnalyzer.analyze(
                                        call,
                                        dependencies
                                )
                        )
                        .toList();

        List<ConditionModel> conditions =
                method.findAll(IfStmt.class)
                        .stream()
                        .map(condition ->
                                conditionAnalyzer.analyze(condition, dependencies))
                        .toList();

        List<ReturnModel> returns =
                method.findAll(ReturnStmt.class)
                        .stream()
                        .map(returnStmt ->
                                new ReturnModel(
                                        returnStmt
                                                .getExpression()
                                                .map(Object::toString)
                                                .orElse("")
                                )
                        )
                        .toList();

        List<AssignmentModel> assignments =
                extractAssignments(method);

        List<ThrowModel> throwsStatements =
                method.findAll(ThrowStmt.class)
                        .stream()
                        .map(this::toThrowModel)
                        .toList();

        return new MethodModel(
                method.getNameAsString(),
                method.getTypeAsString(),
                parameters,
                methodCalls,
                conditions,
                returns,
                assignments,
                throwsStatements
        );
    }

    private ThrowModel toThrowModel(
            ThrowStmt throwStmt) {

        String expression =
                throwStmt.getExpression().toString();

        String exceptionType = "";

        if (throwStmt.getExpression()
                .isObjectCreationExpr()) {

            exceptionType =
                    throwStmt.getExpression()
                            .asObjectCreationExpr()
                            .getType()
                            .asString();
        }

        return new ThrowModel(
                exceptionType,
                expression
        );
    }

    private List<AssignmentModel> extractAssignments(
            MethodDeclaration method) {

        return method.findAll(VariableDeclarationExpr.class)
                .stream()
                .flatMap(variableDeclaration ->
                        variableDeclaration
                                .getVariables()
                                .stream()
                                .map(variable ->
                                        new AssignmentModel(
                                                variable.getNameAsString(),
                                                variable.getTypeAsString(),
                                                variable.getInitializer()
                                                        .map(Object::toString)
                                                        .orElse("")
                                        )
                                )
                )
                .toList();
    }




}