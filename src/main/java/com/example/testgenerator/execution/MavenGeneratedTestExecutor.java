package com.example.testgenerator.execution;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class MavenGeneratedTestExecutor
        implements GeneratedTestExecutor {

    @Override
    public ExecutionResult execute(
            String testClassName,
            Path projectRoot) {

        List<String> command =
                createCommand(
                        testClassName,
                        projectRoot
                );

        ProcessBuilder processBuilder =
                new ProcessBuilder(command)
                        .directory(
                                projectRoot.toFile()
                        )
                        .redirectErrorStream(true);

        try {

            Process process =
                    processBuilder.start();

            List<String> output =
                    new BufferedReader(
                            new InputStreamReader(
                                    process.getInputStream()
                            )
                    )
                    .lines()
                    .toList();

            int exitCode =
                    process.waitFor();

            return new ExecutionResult(
                    exitCode == 0,
                    output
            );

        } catch (InterruptedException exception) {

            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Test execution was interrupted",
                    exception
            );

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Failed to execute generated test",
                    exception
            );
        }
    }

    private List<String> createCommand(
            String testClassName,
            Path projectRoot) {

        Path windowsWrapper =
                projectRoot.resolve("mvnw.cmd");

        if (Files.exists(windowsWrapper)) {

            return List.of(
                    "cmd.exe",
                    "/c",
                    windowsWrapper.toAbsolutePath().toString(),
                    "-q",
                    "-Dtest=" + testClassName,
                    "test"
            );
        }

        Path unixWrapper =
                projectRoot.resolve("mvnw");

        if (Files.exists(unixWrapper)) {

            return List.of(
                    unixWrapper.toAbsolutePath().toString(),
                    "-q",
                    "-Dtest=" + testClassName,
                    "test"
            );
        }

        return List.of(
                "mvn",
                "-q",
                "-Dtest=" + testClassName,
                "test"
        );
    }
}