package com.patra.expr;

import java.util.Objects;

/// 逻辑非表达式。
///
/// 表示对一个子表达式的否定。
public record Not(Expr child) implements Expr {

  /// 规范构造器,强制执行 NOT 表达式的验证规则。
  ///
  /// 验证规则:
  ///
  /// - child 不能为 null
  ///
  /// @throws NullPointerException 如果 child 为 null
  public Not {
    Objects.requireNonNull(child, "child");
  }

  /// 接受访问者访问此 NOT 表达式。
  ///
  /// @param visitor 表达式访问者
  /// @param <R> 访问者返回类型
  /// @return 访问者返回的结果
  @Override
  public <R> R accept(ExprVisitor<R> visitor) {
    return visitor.visitNot(this);
  }
}
