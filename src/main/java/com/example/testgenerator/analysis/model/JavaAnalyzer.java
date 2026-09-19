package com.example.testgenerator.analysis.model;

import java.nio.file.Path;

public interface JavaAnalyzer {

    ClassModel analyze(Path sourceFile);
}