package com.example.testgenerator.analysis.model;

import java.util.List;
import java.util.Optional;

public record DependencyModel(
        String type,
        String name,
        DependencyKind kind
) {
    public static Optional<DependencyModel> findByName(
            List<DependencyModel> dependencies,
            String name) {

        if (name == null || name.isEmpty()) {
            return Optional.empty();
        }

        return dependencies.stream()
                .filter(dependency -> dependency.name().equals(name))
                .findFirst();
    }
}