package com.patra.ingest.app.orchestration.expression;

import com.patra.expr.Expr;

import java.util.Objects;

/**
 * Canonical representation of the business expression used for plan assembly.
 */
public record PlanExpressionDescriptor(Expr expr, String jsonSnapshot, String hash) {
    public PlanExpressionDescriptor {
        Objects.requireNonNull(expr, "expr must not be null");
        jsonSnapshot = jsonSnapshot == null ? "{}" : jsonSnapshot;
        Objects.requireNonNull(hash, "hash must not be null");
    }
}
