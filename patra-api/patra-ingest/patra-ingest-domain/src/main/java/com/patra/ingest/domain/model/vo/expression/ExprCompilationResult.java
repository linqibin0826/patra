package com.patra.ingest.domain.model.vo.expression;

import com.fasterxml.jackson.databind.JsonNode;

/// 表达式编译结果值对象。
/// 
/// 与 patra-spring-boot-starter-expr 的 CompileResult 对齐,但适配于领域层:
/// 
/// - query - 已编译的查询字符串 (例如 PubMed term)
///   - params - 已编译的参数(JSON 格式,例如 retmax, sort)
///   - normalizedExpression - 归一化表达式 JSON
///   - errors - 验证错误消息 (有效时为空)
///   - warnings - 验证警告消息
/// 
/// @param query 已编译的查询字符串
/// @param params 已编译的参数(JSON 格式)
/// @param normalizedExpression 归一化表达式 JSON
/// @param errors 验证错误 (编译成功时为空)
/// @param warnings 验证警告
/// @author linqibin
/// @since 0.1.0
public record ExprCompilationResult(
    String query, JsonNode params, String normalizedExpression, String errors, String warnings) {
  /// Check if compilation succeeded (no errors).
  public boolean isValid() {
    return errors == null || errors.isBlank();
  }

  /// Get validation message (errors + warnings).
  public String validationMessage() {
    if (errors != null && !errors.isBlank()) {
      return warnings != null && !warnings.isBlank()
          ? "Errors: " + errors + "; Warnings: " + warnings
          : errors;
    }
    return warnings != null && !warnings.isBlank() ? "Warnings: " + warnings : null;
  }

  /// Create success result.
  public static ExprCompilationResult success(
      String query, JsonNode params, String normalizedExpression, String warnings) {
    return new ExprCompilationResult(query, params, normalizedExpression, null, warnings);
  }

  /// Create failure result.
  public static ExprCompilationResult failure(String errors) {
    return new ExprCompilationResult(null, null, null, errors, null);
  }
}
