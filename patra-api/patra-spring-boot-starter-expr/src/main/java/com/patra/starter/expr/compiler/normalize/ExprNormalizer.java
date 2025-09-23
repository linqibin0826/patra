package com.patra.starter.expr.compiler.normalize;

import com.patra.expr.Expr;
import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot;

public interface ExprNormalizer {
    Expr normalize(Expr expression,  boolean strictMode);
}
