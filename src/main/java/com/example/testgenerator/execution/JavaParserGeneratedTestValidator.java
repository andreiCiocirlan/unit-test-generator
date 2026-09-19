package com.example.testgenerator.execution;

import com.github.javaparser.StaticJavaParser;
import org.springframework.stereotype.Component;

@Component
public class JavaParserGeneratedTestValidator
        implements GeneratedTestValidator {

    @Override
    public void validate(String source) {

        try {
            StaticJavaParser.parse(source);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Generated test contains invalid Java syntax",
                    exception
            );
        }
    }
}