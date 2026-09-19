package com.example.testgenerator.analysis;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Optional;

@Component
public class ConstructorResolver {

    public Optional<ConstructorDeclaration> resolve(
            ClassOrInterfaceDeclaration classDeclaration) {

        var constructors = classDeclaration.getConstructors();

        if (constructors.isEmpty()) {
            return Optional.empty();
        }

        var autowiredConstructor = constructors.stream()
                .filter(constructor ->
                        constructor.getAnnotations()
                                .stream()
                                .anyMatch(annotation ->
                                        annotation.getNameAsString()
                                                .equals("Autowired")))
                .findFirst();

        if (autowiredConstructor.isPresent()) {
            return autowiredConstructor;
        }

        if (constructors.size() == 1) {
            return Optional.of(constructors.getFirst());
        }

        return constructors.stream()
                .max(Comparator.comparingInt(
                        constructor ->
                                constructor.getParameters().size()
                ));
    }
}