package com.example.testgenerator.analysis;

import com.example.testgenerator.analysis.model.SpringType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SpringTypeClassifier {

    public SpringType classify(List<String> annotations) {

        if (annotations.contains("RestController")) {
            return SpringType.REST_CONTROLLER;
        }

        if (annotations.contains("Controller")) {
            return SpringType.CONTROLLER;
        }

        if (annotations.contains("Service")) {
            return SpringType.SERVICE;
        }

        if (annotations.contains("Repository")) {
            return SpringType.REPOSITORY;
        }

        if (annotations.contains("Component")) {
            return SpringType.COMPONENT;
        }

        return SpringType.UNKNOWN;
    }
}