package com.patra.ingest.domain.model.vo;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Expression compilation result.
 *
 * <p>Aligned with CompileResult from patra-spring-boot-starter-expr but adapted for domain layer:
 *
 * <ul>
 *   <li>query - Compiled query string (e.g., PubMed term)
 *   <li>params - Compiled parameters as JSON (e.g., retmax, sort)
 *   <li>normalizedExpression - Normalized expression JSON
 *   <li>errors - Validation error messages (empty if valid)
 *   <li>warnings - Validation warning messages
 * </ul>
 *
 * @param query compiled query string
 * @param params compiled parameters as JSON
 * @param normalizedExpression normalized expression JSON
 * @param errors validation errors (empty if compilation succeeded)
 * @param warnings validation warnings
 * @author linqibin
 * @since 0.1.0
 */
public record ExprCompilationResult(
    String query, JsonNode params, String normalizedExpression, String errors, String warnings) {
  /** Check if compilation succeeded (no errors). */
  public boolean isValid() {
    return errors == null || errors.isBlank();
  }

  /** Get validation message (errors + warnings). */
  public String validationMessage() {
    if (errors != null && !errors.isBlank()) {
      return warnings != null && !warnings.isBlank()
          ? "Errors: " + errors + "; Warnings: " + warnings
          : errors;
    }
    return warnings != null && !warnings.isBlank() ? "Warnings: " + warnings : null;
  }

  /** Create success result. */
  public static ExprCompilationResult success(
      String query, JsonNode params, String normalizedExpression, String warnings) {
    return new ExprCompilationResult(query, params, normalizedExpression, null, warnings);
  }

  /** Create failure result. */
  public static ExprCompilationResult failure(String errors) {
    return new ExprCompilationResult(null, null, null, errors, null);
  }
}
