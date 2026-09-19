package com.example.testgenerator.execution;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class MavenGeneratedTestCompiler
        implements GeneratedTestCompiler {

    @Override
    public CompilationResult compile(
            Path sourceFile,
            Path projectRoot) {

        ProcessBuilder processBuilder =
                new ProcessBuilder(
                        createCommand(projectRoot)
                );

        processBuilder.directory(
                projectRoot.toFile()
        );

        processBuilder.redirectErrorStream(true);

        try {

            Process process =
                    processBuilder.start();

            String output =
                    new String(
                            process.getInputStream()
                                    .readAllBytes()
                    );

            int exitCode =
                    process.waitFor();

            if (exitCode == 0) {
                return new CompilationResult(
                        true,
                        List.of()
                );
            }

            return new CompilationResult(
                    false,
                    List.of(output)
            );

        } catch (IOException exception) {

            return new CompilationResult(
                    false,
                    List.of(
                            "Failed to execute Maven: "
                            + exception.getMessage()
                    )
            );

        } catch (InterruptedException exception) {

            Thread.currentThread().interrupt();

            return new CompilationResult(
                    false,
                    List.of(
                            "Maven compilation was interrupted"
                    )
            );
        }
    }

    private List<String> createCommand(
            Path projectRoot) {

        Path windowsWrapper =
                projectRoot.resolve("mvnw.cmd");

        if (Files.exists(windowsWrapper)) {

            return List.of(
                    "cmd.exe",
                    "/c",
                    windowsWrapper.toAbsolutePath().toString(),
                    "-q",
                    "-DskipTests",
                    "test-compile"
            );
        }

        Path unixWrapper =
                projectRoot.resolve("mvnw");

        if (Files.exists(unixWrapper)) {

            return List.of(
                    unixWrapper.toAbsolutePath().toString(),
                    "-q",
                    "-DskipTests",
                    "test-compile"
            );
        }

        return List.of(
                "mvn",
                "-q",
                "-DskipTests",
                "test-compile"
        );
    }
}