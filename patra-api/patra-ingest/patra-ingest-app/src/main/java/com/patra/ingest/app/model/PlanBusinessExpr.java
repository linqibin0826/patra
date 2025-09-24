package com.patra.ingest.app.model;

import com.patra.expr.Expr;

import java.util.Objects;

/**
 * Plan 级业务表达式（内存对象 + JSON 快照 + hash）。
 */
public record PlanBusinessExpr(Expr expr, String jsonSnapshot, String hash) {
    public PlanBusinessExpr {
        Objects.requireNonNull(expr, "expr不能为空");
        jsonSnapshot = jsonSnapshot == null ? "{}" : jsonSnapshot;
        Objects.requireNonNull(hash, "hash不能为空");
    }
}
