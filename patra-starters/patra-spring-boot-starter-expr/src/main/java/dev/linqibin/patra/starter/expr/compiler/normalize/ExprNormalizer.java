package dev.linqibin.patra.starter.expr.compiler.normalize;

import dev.linqibin.patra.expr.Expr;

/// 表达式规范化器,将表达式转换为规范形式。
///
/// 规范化包括简化布尔逻辑、展平嵌套、去除冗余等。
///
/// @author linqibin
/// @since 0.1.0
public interface ExprNormalizer {
  /// 规范化表达式。
  ///
  /// @param expression 待规范化的表达式
  /// @param strictMode 严格模式
  /// @return 规范化后的表达式
  Expr normalize(Expr expression, boolean strictMode);
}
