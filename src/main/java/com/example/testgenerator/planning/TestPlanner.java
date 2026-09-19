package com.example.testgenerator.planning;

import com.example.testgenerator.analysis.model.ClassModel;
import com.example.testgenerator.planning.model.TestScenario;

import java.util.List;

public interface TestPlanner {

    List<TestScenario> plan(ClassModel classModel);
}