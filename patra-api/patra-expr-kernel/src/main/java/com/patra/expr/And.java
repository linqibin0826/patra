package com.patra.expr;

import java.util.List;
import java.util.Objects;

/// 逻辑合取表达式。
///
/// 表示多个表达式的与逻辑结合。
public record And(List<Expr> children) implements Expr {

  public And {
    Objects.requireNonNull(children, "children");
    if (children.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("AND 表达式不能包含空的子表达式");
    }
    children = List.copyOf(children);
  }

  @Override
  public <R> R accept(ExprVisitor<R> visitor) {
    return visitor.visitAnd(this);
  }
}
