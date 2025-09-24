package com.patra.expr;

/**
 * Root interface of the expression abstract syntax tree used by the Papertrace platform.
 * <p>
 * Every concrete node is immutable and thread-safe.  Consumers are expected to
 * traverse the tree via the provided {@link Visitor} interface instead of relying on
 * reflection or implementation details.
 * </p>
 */
public sealed interface Expr permits And, Or, Not, Const, Atom {
    /**
     * Convenience shortcut for {@code this == Const.TRUE}.
     */
    default boolean isConstTrue() {
        return this == Const.TRUE;
    }

    /**
     * Convenience shortcut for {@code this == Const.FALSE}.
     */
    default boolean isConstFalse() {
        return this == Const.FALSE;
    }
}
