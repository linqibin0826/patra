package com.patra.expr;

/**
 * Boolean constant expression.
 */
public enum Const implements Expr {
    TRUE,
    FALSE;

    @Override
    public <R> R accept(ExprVisitor<R> visitor) {
        return visitor.visitConst(this);
    }
}
