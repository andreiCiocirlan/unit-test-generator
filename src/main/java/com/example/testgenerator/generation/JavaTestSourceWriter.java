package com.example.testgenerator.generation;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class JavaTestSourceWriter
        implements TestSourceWriter {

    @Override
    public Path write(
            String packageName,
            String className,
            String source,
            Path projectRoot) {

        Path testDirectory =
                projectRoot.resolve("src/test/java");

        if (!packageName.isBlank()) {
            testDirectory = testDirectory.resolve(
                    packageName.replace(".", "/")
            );
        }

        Path outputFile =
                testDirectory.resolve(
                        className + ".java"
                );

        try {

            Files.createDirectories(
                    testDirectory
            );

            Files.writeString(
                    outputFile,
                    source
            );

            return outputFile;

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Failed to write generated test to "
                    + outputFile,
                    exception
            );
        }
    }
}