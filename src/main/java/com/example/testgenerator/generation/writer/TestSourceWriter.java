package com.example.testgenerator.generation.writer;

import java.nio.file.Path;

public interface TestSourceWriter {

    Path write(
            String packageName,
            String className,
            String source,
            Path projectRoot
    );
}