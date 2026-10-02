package com.example.testgenerator.planning;

import com.example.testgenerator.analysis.DtoAnalyzer;
import com.example.testgenerator.analysis.model.DtoModel;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    /** Resolve a DTO type, or empty if sourceRoot is unset or the type is unknown. */
    private Optional<DtoModel> resolveDto(String type) {
        if (sourceRoot == null) return Optional.empty();
        return dtoAnalyzer.resolve(type, sourceRoot, imports);
    }

    /** True if the DTO is a concrete class (not an interface or abstract class). */
    private boolean isConcrete(DtoModel dto) {
        return !dto.isInterface() && !dto.isAbstract();
    }

    /** True if the type is concrete and instantiable with `new Type()`. */
    public boolean isInstantiableNoArg(String type) {
        var d = resolveDto(type).orElse(null);
        if (d == null) return false;
        return isConcrete(d) && d.hasNoArgConstructor();
    }

    /** True if the type has an all-args constructor (value-object style). */
    public boolean isInstantiableAllArgs(String type) {
        var d = resolveDto(type).orElse(null);
        if (d == null) return false;
        return isConcrete(d)
               && d.hasAllArgsConstructor()
               && !d.fields().isEmpty();
    }

    /**
     * If the type has an all-args constructor, return `new Type(v1, v2, ...)`
     * with defaults for each parameter. Otherwise return null.
     */
    public String allArgsConstructorInitializer(String type) {
        var d = resolveDto(type).orElse(null);
        if (d == null) return null;
        if (!isConcrete(d) || !d.hasAllArgsConstructor()) {
            return null;
        }

        StringBuilder sb = new StringBuilder("new ")
                .append(TypeValueSupport.simpleName(type))
                .append("(");
        for (int i = 0; i < d.fields().size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(valueFor(d.fields().get(i).type()));
        }
        sb.append(")");
        return sb.toString();
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
        String collectionDefault = TypeValueSupport.collectionDefault(type);
        if (collectionDefault != null) {
            return collectionDefault;
        }

        var nested = resolveDto(type);
        if (nested.isPresent()) {
            return buildDtoInitializer(nested.get(), overrides);
        }

        return "mock(" + TypeValueSupport.eraseGenerics(type) + ".class)";
    }

    private String buildDtoInitializer(
            DtoModel dto,
            Map<String, String> overrides) {

        if (dto.hasBuilder()) {
            StringBuilder sb = new StringBuilder(TypeValueSupport.simpleName(dto.qualifiedName()))
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

        return "mock(" + TypeValueSupport.simpleName(dto.qualifiedName()) + ".class)";
    }
}