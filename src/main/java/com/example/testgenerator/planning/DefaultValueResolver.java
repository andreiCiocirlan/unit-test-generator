package com.example.testgenerator.planning;

import com.example.testgenerator.analysis.DtoAnalyzer;
import com.example.testgenerator.analysis.model.DtoModel;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

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

    /** Existing single-arg entry point: no field overrides. */
    public String valueFor(String type) {
        return valueFor(type, Map.of());
    }

    /**
     * Same as valueFor(type), but for a DTO type the given fields are set
     * to the given literal values instead of the type's default literal.
     * For non-DTO types the overrides are ignored.
     */
    public String valueFor(String type, Map<String, String> overrides) {

        // Scalars are never affected by overrides.
        String scalar = scalarValueFor(type);
        if (scalar != null) {
            return scalar;
        }

        return defaultForComplex(type, overrides);
    }

    private String scalarValueFor(String type) {
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
            default -> null;
        };
    }

    private String defaultForComplex(
            String type,
            Map<String, String> overrides) {

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

        if (sourceRoot != null) {
            var nested = dtoAnalyzer.resolve(type, sourceRoot, imports);
            if (nested.isPresent()) {
                return buildDtoInitializer(nested.get(), overrides);
            }
        }

        return "mock(" + eraseGenerics(type) + ".class)";
    }

    private String buildDtoInitializer(
            DtoModel dto,
            Map<String, String> overrides) {

        if (dto.hasBuilder()) {
            StringBuilder sb = new StringBuilder(simpleName(dto.qualifiedName()))
                    .append(".builder()");
            for (var f : dto.fields()) {
                String value = overrides.containsKey(f.name())
                        ? overrides.get(f.name())
                        : valueFor(f.type(), Map.of());
                sb.append(".").append(f.name())
                        .append("(").append(value).append(")");
            }
            sb.append(".build()");
            return sb.toString();
        }

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