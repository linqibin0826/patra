package com.patra.ingest.app.orchestration.expression;

import com.patra.expr.Expr;

import java.util.Objects;

/**
 * Canonical representation of the business expression used for plan assembly.
 */
public record PlanExpressionDescriptor(Expr expr, String jsonSnapshot, String hash) {
    public PlanExpressionDescriptor {
        Objects.requireNonNull(expr, "expr不能为空");
        jsonSnapshot = jsonSnapshot == null ? "{}" : jsonSnapshot;
        Objects.requireNonNull(hash, "hash不能为空");
    }
}
