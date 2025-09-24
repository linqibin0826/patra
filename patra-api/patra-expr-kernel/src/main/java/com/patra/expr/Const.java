package com.patra.expr;

/**
 * Boolean constant expression.
 */
public enum Const implements Expr {
    TRUE,
    FALSE;

    @Override
    public <R> R accept(Visitor<R> visitor) {
        return visitor.visitConst(this);
    }
}
