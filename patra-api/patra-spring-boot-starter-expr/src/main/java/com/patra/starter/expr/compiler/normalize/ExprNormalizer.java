package com.patra.starter.expr.compiler.normalize;

import com.patra.expr.Expr;

public interface ExprNormalizer {
    Expr normalize(Expr expression,  boolean strictMode);
}
