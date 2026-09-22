package com.example.testgenerator.analysis;

import com.example.testgenerator.analysis.model.DtoModel;
import com.example.testgenerator.analysis.model.FieldModel;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Component
public class DtoAnalyzer {

    private static final List<String> DTO_PACKAGE_HINTS = List.of(
            "dto", "model", "request", "response", "command", "event"
    );

    private final Map<String, Optional<DtoModel>> cache = new ConcurrentHashMap<>();

    public Optional<DtoModel> resolve(
            String typeName,
            Path sourceRoot,
            List<String> imports) {

        String key = typeName + "@" + sourceRoot;
        return cache.computeIfAbsent(
                key,
                k -> doResolve(typeName, sourceRoot, imports)
        );
    }

    private Optional<DtoModel> doResolve(
            String typeName,
            Path sourceRoot,
            List<String> imports) {

        Path file = sourceFileFor(typeName, sourceRoot, imports);
        if (file == null || !Files.exists(file)) {
            return Optional.empty();
        }

        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            ClassOrInterfaceDeclaration decl = cu
                    .findFirst(ClassOrInterfaceDeclaration.class)
                    .orElse(null);

            if (decl == null) {
                return Optional.empty();
            }

            boolean hasBuilder = decl.getAnnotations().stream()
                    .anyMatch(a -> a.getNameAsString().equals("Builder"));

            List<FieldModel> fields = new ArrayList<>();
            for (FieldDeclaration field : decl.getFields()) {
                if (field.hasModifier(Modifier.Keyword.STATIC)) continue;
                for (VariableDeclarator v : field.getVariables()) {
                    fields.add(new FieldModel(
                            v.getNameAsString(),
                            v.getTypeAsString()
                    ));
                }
            }

            String pkg = cu.getPackageDeclaration()
                    .map(p -> p.getNameAsString())
                    .orElse("");

            String qualified = pkg.isEmpty()
                    ? decl.getNameAsString()
                    : pkg + "." + decl.getNameAsString();

            return Optional.of(new DtoModel(qualified, hasBuilder, fields));

        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Path sourceFileFor(
            String typeName,
            Path sourceRoot,
            List<String> imports) {

        // 1. Fully qualified: com.example.notification.NotificationRequest
        if (typeName.contains(".")) {
            Path direct = sourceRoot.resolve(
                    typeName.replace('.', '/') + ".java");
            if (Files.exists(direct)) return direct;
        }

        // 2. Explicit single-type import ending with "." + typeName
        if (imports != null) {
            for (String imp : imports) {
                if (imp.endsWith(".*")) continue;
                if (imp.endsWith("." + typeName)) {
                    Path p = sourceRoot.resolve(
                            imp.replace('.', '/') + ".java");
                    if (Files.exists(p)) return p;
                }
            }
        }

        // 3. Well-known DTO/POJO packages under the source root
        for (String pkg : DTO_PACKAGE_HINTS) {
            Path p = sourceRoot.resolve(pkg).resolve(typeName + ".java");
            if (Files.exists(p)) return p;
        }

        // 4. Wildcard imports: com.example.notification.dto.*
        if (imports != null) {
            for (String imp : imports) {
                if (!imp.endsWith(".*")) continue;
                String base = imp.substring(0, imp.length() - 2);
                Path p = sourceRoot.resolve(
                        base.replace('.', '/')
                        + "/" + typeName + ".java");
                if (Files.exists(p)) return p;
            }
        }

        // 5. Cached walk as last resort
        return walkFor(typeName, sourceRoot);
    }

    private Path walkFor(String typeName, Path sourceRoot) {
        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            return stream
                    .filter(p -> p.getFileName()
                            .toString()
                            .equals(typeName + ".java"))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}