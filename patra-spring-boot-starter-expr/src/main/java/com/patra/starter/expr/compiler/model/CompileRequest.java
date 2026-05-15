package com.patra.starter.expr.compiler.model;

import dev.linqibin.patra.common.enums.ProvenanceCode;
import dev.linqibin.patra.expr.Expr;
import java.util.Locale;
import java.util.Objects;

/// 表达式编译请求。
///
/// 包含表达式、溯源代码、操作类型、端点名称和编译选项。
///
/// @param expression 待编译的表达式
/// @param provenance 溯源代码
/// @param operationType 操作类型(如 SEARCH、HARVEST、UPDATE)
/// @param endpointName 端点名称
/// @param options 编译选项
/// @author linqibin
/// @since 0.1.0
public record CompileRequest(
    Expr expression,
    ProvenanceCode provenance,
    String operationType,
    String endpointName,
    CompileOptions options) {
  public CompileRequest {
    Objects.requireNonNull(expression, "expression");
    Objects.requireNonNull(provenance, "provenance");
    endpointName = normalizeOperation(endpointName);
    options = options == null ? CompileOptions.defaults() : options;
  }

  private static String normalizeOperation(String op) {
    if (op == null || op.isBlank()) {
      return "SEARCH";
    }
    return op.trim().toUpperCase(Locale.ROOT);
  }
}
