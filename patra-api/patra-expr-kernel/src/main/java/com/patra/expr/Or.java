package com.patra.expr;

import java.util.List;
import java.util.Objects;

/// 逻辑析取表达式。
///
/// 表示多个表达式的或逻辑结合。
public record Or(List<Expr> children) implements Expr {

  public Or {
    Objects.requireNonNull(children, "children");
    if (children.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("OR 表达式不能包含空的子表达式");
    }
    children = List.copyOf(children);
  }

  @Override
  public <R> R accept(ExprVisitor<R> visitor) {
    return visitor.visitOr(this);
  }
}
