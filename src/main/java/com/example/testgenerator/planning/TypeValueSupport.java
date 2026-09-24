package com.example.testgenerator.planning;

import com.example.testgenerator.analysis.model.CallKind;
import com.example.testgenerator.analysis.model.MethodCallModel;
import com.example.testgenerator.analysis.model.MethodModel;
import com.example.testgenerator.analysis.model.ReturnModel;

/**
 * Pure helpers for reasoning about Java type names and default values.
 * No knowledge of MethodModel, scenarios, or planning state.
 */
public final class TypeValueSupport {

    private TypeValueSupport() {}

    public static boolean isWellKnownImmutable(String type) {
        return switch (type) {
            case "String", "Long", "Integer", "int", "long",
                 "Double", "double", "Float", "float",
                 "Boolean", "boolean", "Short", "short",
                 "Byte", "byte", "Character", "char" -> true;
            default -> false;
        };
    }

    public static boolean isPrimitiveType(String type) {
        return switch (type) {
            case "int", "long", "short", "byte",
                 "double", "float", "boolean", "char" -> true;
            default -> false;
        };
    }

    public static boolean isCollectionType(String type) {
        if (type == null) return false;
        return type.startsWith("List<")
               || type.startsWith("java.util.List<")
               || type.startsWith("Set<")
               || type.startsWith("java.util.Set<")
               || type.startsWith("Map<")
               || type.startsWith("java.util.Map<")
               || type.equals("List")
               || type.equals("Set")
               || type.equals("Map");
    }

    public static String eraseGenerics(String type) {
        int idx = type.indexOf('<');
        return idx < 0 ? type : type.substring(0, idx);
    }

    public static String simpleName(String type) {
        if (type == null || type.isBlank()) return type;
        int generic = type.indexOf('<');
        String noGenerics = generic < 0 ? type : type.substring(0, generic);
        int lastDot = noGenerics.lastIndexOf('.');
        return lastDot < 0 ? noGenerics : noGenerics.substring(lastDot + 1);
    }

    public static String innerTypeOf(String type) {
        if (type == null) return type;
        int lt = type.indexOf('<');
        int gt = type.lastIndexOf('>');
        if (lt < 0 || gt < 0 || gt <= lt) {
            return type;
        }
        return type.substring(lt + 1, gt).trim();
    }

    public static String defaultValueFor(String type) {
        if (type != null && type.startsWith("Optional<")) {
            return "java.util.Optional.empty()";
        }
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
            default -> "null";
        };
    }

    /**
     * Sensible default literal for a getter-style method, or null if we
     * don't know how to stub it.
     */
    public static String defaultReturnForGetter(MethodCallModel call) {
        String name = call.methodName();

        // Real getters take no arguments.
        if (!call.arguments().isEmpty()) {
            return null;
        }

        if (name.startsWith("is")) {
            return "true";
        }

        if (name.startsWith("get")) {
            if (name.equals("getMessageId")
                || name.endsWith("Message")
                || name.endsWith("Id")
                || name.endsWith("Name")
                || name.endsWith("Status")) {
                return "\"msg-123\"";
            }
            return "\"test-value\"";
        }

        return null;
    }

    public static boolean isOptionalOrElseThrow(
            MethodModel method,
            MethodCallModel call) {

        if (call.kind() != CallKind.DEPENDENCY) {
            return false;
        }

        return method.returns().stream()
                .map(ReturnModel::expression)
                .anyMatch(e -> e.contains(
                        call.target() + "." + call.methodName())
                               && e.endsWith(".orElseThrow()"));
    }

    public static String expectedVariableName(MethodModel method) {
        String returnType = method.returnType();
        if (returnType == null || returnType.isBlank()) {
            return "expectedValue";
        }

        String simple = TypeValueSupport.simpleName(
                TypeValueSupport.eraseGenerics(returnType)
        );
        return "expected"
               + Character.toUpperCase(simple.charAt(0))
               + simple.substring(1);
    }
}