package com.patra.expr;

/**
 * Root interface for the expression abstract syntax tree used across the Papertrace platform.
 *
 * <p>The tree models boolean logic with immutable leaf atoms only; it deliberately avoids any
 * platform- or data-source-specific details. Rendering and escaping are delegated to translators
 * that run after normalization, degradation, and capability checks.
 *
 * <p>Thread safety: all implementations (records/enums) are immutable and can be freely shared
 * across threads. Resource usage: nodes never hold external resources nor participate in
 * transactional boundaries. Typical usage: construct expression trees via {@link Exprs} factory
 * methods before handing them to normalization and validation pipelines.
 *
 * @author linqibin
 * @since 0.1.0
 */
public sealed interface Expr permits And, Or, Not, Const, Atom {

  /**
   * Accepts the supplied visitor and delegates to the matching handler.
   *
   * @param visitor visitor to invoke
   * @param <R> visitor return type
   * @return visitor result
   */
  <R> R accept(ExprVisitor<R> visitor);

  /**
   * Convenience shortcut for {@code this == Const.TRUE}.
   *
   * @return {@code true} when this node is the boolean constant TRUE
   */
  default boolean isConstTrue() {
    return this == Const.TRUE;
  }

  /**
   * Convenience shortcut for {@code this == Const.FALSE}.
   *
   * @return {@code true} when this node is the boolean constant FALSE
   */
  default boolean isConstFalse() {
    return this == Const.FALSE;
  }
}
