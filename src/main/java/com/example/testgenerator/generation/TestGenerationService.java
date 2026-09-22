package com.example.testgenerator.generation;

import com.example.testgenerator.analysis.model.ClassModel;
import com.example.testgenerator.analysis.model.JavaAnalyzer;
import com.example.testgenerator.execution.GeneratedTestValidator;
import com.example.testgenerator.generation.model.GeneratedTest;
import com.example.testgenerator.generation.model.TestRenderModel;
import com.example.testgenerator.planning.TestPlanner;
import com.example.testgenerator.planning.model.TestScenario;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Component
public class TestGenerationService {

    private final JavaAnalyzer analyzer;
    private final TestPlanner planner;
    private final JavaTestRenderer renderer;
    private final GeneratedTestValidator validator;

    public TestGenerationService(
            JavaAnalyzer analyzer,
            TestPlanner planner,
            JavaTestRenderer renderer,
            GeneratedTestValidator validator) {

        this.analyzer = analyzer;
        this.planner = planner;
        this.renderer = renderer;
        this.validator = validator;
    }

    public GeneratedTest generate(Path projectRoot, Path sourceFile) {

        ClassModel classModel =
                analyzer.analyze(sourceFile);

        planner.configure(projectRoot, classModel.imports());

        List<TestScenario> scenarios =
                planner.plan(classModel);

        if (scenarios.isEmpty()) {
            throw new IllegalStateException(
                    "No test scenarios generated for "
                    + classModel.className()
            );
        }

        TestRenderModel renderModel =
                new TestRenderModel(
                        classModel.packageName(),
                        classModel.className() + "Test",
                        classModel.className(),
                        classModel.dependencies(),
                        scenarios
                );

        String generatedSource =
                renderer.render(renderModel);

        validator.validate(generatedSource);

        return new GeneratedTest(
                renderModel.packageName(),
                renderModel.className(),
                generatedSource
        );
    }

    public String generateSource(Path projectRoot, Path sourceFile) {
        return generate(projectRoot, sourceFile).source();
    }
}