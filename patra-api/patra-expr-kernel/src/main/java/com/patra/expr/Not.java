package com.patra.expr;

import java.util.Objects;

/**
 * Logical negation of a child expression.
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
