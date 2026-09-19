package com.example.testgenerator.analysis;

import com.example.testgenerator.analysis.model.ConditionModel;
import com.github.javaparser.ast.stmt.IfStmt;

public interface ConditionAnalyzer {

    ConditionModel analyze(IfStmt ifStmt);
}