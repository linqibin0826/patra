package com.patra.starter.expr.compiler.model;

import java.util.Map;
import java.util.Objects;

public record Issue(IssueSeverity severity, String code, String message, Map<String, Object> context) {
    public Issue {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(code, "code");
        message = message == null ? "" : message;
        if (context != null) {
            context = Map.copyOf(context);
        }
    }

    public static Issue error(String code, String message, Map<String, Object> context) {
        return new Issue(IssueSeverity.ERROR, code, message, context);
    }

    public static Issue warn(String code, String message, Map<String, Object> context) {
        return new Issue(IssueSeverity.WARN, code, message, context);
    }
}
