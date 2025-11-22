package com.patra.expr;

/// 布尔常量表达式。
/// 
/// 表示始终为真或始终为假的表达式。
public enum Const implements Expr {
  /// 真常量。
  TRUE,

  /// 假常量。
  FALSE;

  @Override
  public <R> R accept(ExprVisitor<R> visitor) {
    return visitor.visitConst(this);
  }
}
