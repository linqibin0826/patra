package com.patra.expr;

/// 布尔常量表达式。
///
/// 表示始终为真或始终为假的表达式。
public enum Const implements Expr {
  /// 真常量。
  TRUE,

  /// 假常量。
  FALSE;

  /// 接受访问者访问此常量表达式。
  ///
  /// @param visitor 表达式访问者
  /// @param <R> 访问者返回类型
  /// @return 访问者返回的结果
  @Override
  public <R> R accept(ExprVisitor<R> visitor) {
    return visitor.visitConst(this);
  }
}
