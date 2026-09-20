package com.example.testgenerator.execution;

import java.nio.file.Path;

public interface GeneratedTestExecutor {

    ExecutionResult execute(
            String testClassName,
            Path projectRoot
    );
}