package com.patra.expr;

import java.util.Objects;

/**
 * 逻辑非表达式。
 *
 * <p>表示对一个子表达式的否定。
 */
public record Not(Expr child) implements Expr {

  public Not {
    Objects.requireNonNull(child, "child");
  }

  @Override
  public <R> R accept(ExprVisitor<R> visitor) {
    return visitor.visitNot(this);
  }
}
