package com.example.testgenerator.planning;

import com.example.testgenerator.analysis.DtoAnalyzer;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Component
public class DefaultValueResolver {

    private final DtoAnalyzer dtoAnalyzer;
    private Path sourceRoot;
    private List<String> imports = List.of();

    public DefaultValueResolver(DtoAnalyzer dtoAnalyzer) {
        this.dtoAnalyzer = dtoAnalyzer;
    }

    public void configure(Path sourceRoot, List<String> imports) {
        this.sourceRoot = sourceRoot;
        this.imports = imports == null ? List.of() : imports;
    }

    public String valueFor(String type) {
        return switch (type) {
            case "String" -> "\"test@example.com\"";
            case "Long", "long" -> "1L";
            case "Integer", "int" -> "1";
            case "Double", "double" -> "1.0";
            case "Float", "float" -> "1.0f";
            case "Boolean", "boolean" -> "true";
            case "Short", "short" -> "(short) 1";
            case "Byte", "byte" -> "(byte) 1";
            case "Character", "char" -> "'a'";
            case "Instant", "java.time.Instant" ->
                    "java.time.Instant.parse(\"2024-01-01T00:00:00Z\")";
            case "BigDecimal", "java.math.BigDecimal" ->
                    "java.math.BigDecimal.ONE";
            default -> defaultForComplex(type);
        };
    }

    private String defaultForComplex(String type) {

        if (type.startsWith("Optional<")) {
            return "java.util.Optional.empty()";
        }
        if (type.startsWith("List<") || type.startsWith("java.util.List<")) {
            return "java.util.List.of()";
        }
        if (type.startsWith("Set<") || type.startsWith("java.util.Set<")) {
            return "java.util.Set.of()";
        }
        if (type.startsWith("Map<") || type.startsWith("java.util.Map<")) {
            return "java.util.Map.of()";
        }

        // Nested DTO? Try to resolve and recursively build.
        if (sourceRoot != null) {
            var nested = dtoAnalyzer.resolve(type, sourceRoot, imports);
            if (nested.isPresent()) {
                return buildDtoInitializer(nested.get());
            }
        }

        // Last resort: a mock. Note the eraseGenerics fix for anything
        // with type parameters.
        return "mock(" + eraseGenerics(type) + ".class)";
    }

    private String buildDtoInitializer(
            com.example.testgenerator.analysis.model.DtoModel dto) {

        if (dto.hasBuilder()) {
            StringBuilder sb = new StringBuilder(simpleName(dto.qualifiedName()))
                    .append(".builder()");
            for (var f : dto.fields()) {
                sb.append(".").append(f.name())
                  .append("(").append(valueFor(f.type())).append(")");
            }
            sb.append(".build()");
            return sb.toString();
        }

        // No builder: fall back to a mock.
        return "mock(" + simpleName(dto.qualifiedName()) + ".class)";
    }

    private String simpleName(String qualified) {
        int dot = qualified.lastIndexOf('.');
        return dot < 0 ? qualified : qualified.substring(dot + 1);
    }

    private String eraseGenerics(String type) {
        int idx = type.indexOf('<');
        return idx < 0 ? type : type.substring(0, idx);
    }
}