package com.patra.starter.expr.compiler.model;

import java.util.Map;
import java.util.Objects;

public record Issue(
    IssueSeverity severity, String code, String message, Map<String, Object> context) {
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

  /**
   * Custom toString to prevent infinite recursion when context contains complex objects.
   *
   * <p>Avoids calling toString() on context values, which may contain Expr or other objects with
   * deep/circular references that cause StackOverflowError during debugging.
   */
  @Override
  public String toString() {
    String contextSummary =
        context == null
            ? "null"
            : "Map{size=" + context.size() + ", keys=" + context.keySet() + "}";
    return "Issue[severity="
        + severity
        + ", code="
        + code
        + ", message="
        + message
        + ", context="
        + contextSummary
        + "]";
  }
}
