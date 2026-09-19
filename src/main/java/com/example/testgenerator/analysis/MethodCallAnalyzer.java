package com.example.testgenerator.analysis;

import com.example.testgenerator.analysis.model.DependencyModel;
import com.example.testgenerator.analysis.model.MethodCallModel;
import com.github.javaparser.ast.expr.MethodCallExpr;

import java.util.List;

public interface MethodCallAnalyzer {

    MethodCallModel analyze(
            MethodCallExpr methodCall,
            List<DependencyModel> dependencies
    );
}