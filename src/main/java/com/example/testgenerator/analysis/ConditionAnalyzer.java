package com.example.testgenerator.analysis;

import com.example.testgenerator.analysis.model.ConditionModel;
import com.example.testgenerator.analysis.model.DependencyModel;
import com.github.javaparser.ast.stmt.IfStmt;

import java.util.List;

public interface ConditionAnalyzer {

    ConditionModel analyze(
            IfStmt ifStmt,
            List<DependencyModel> dependencies
    );
}