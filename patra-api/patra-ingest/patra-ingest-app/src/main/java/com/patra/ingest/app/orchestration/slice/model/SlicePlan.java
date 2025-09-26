package com.patra.ingest.app.orchestration.slice.model;

import com.patra.expr.Expr;
import java.time.Instant;
import java.util.Objects;

/**
 * Intermediate slice representation before persistence.
 */
public record SlicePlan(int sequence,
                        String sliceSignatureSeed,
                        String sliceSpecJson,
                        Expr sliceExpr,
                        String sliceExprJson,
                        String sliceExprHash,
                        Instant windowFrom,
                        Instant windowTo) {
    public SlicePlan {
        Objects.requireNonNull(sliceSignatureSeed, "sliceSignatureSeed不能为空");
        Objects.requireNonNull(sliceSpecJson, "sliceSpecJson不能为空");
        Objects.requireNonNull(sliceExprJson, "sliceExprJson不能为空");
        Objects.requireNonNull(sliceExprHash, "sliceExprHash不能为空");
    }
}
