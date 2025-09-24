package com.patra.expr;

import java.util.List;
import java.util.Objects;

/**
 * Logical disjunction of {@link Expr} nodes.
 */
public record Or(List<Expr> children) implements Expr {

    public Or {
        Objects.requireNonNull(children, "children");
        if (children.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("OR expression cannot contain null children");
        }
        children = List.copyOf(children);
    }

    @Override
    public <R> R accept(ExprVisitor<R> visitor) {
        return visitor.visitOr(this);
    }
}
