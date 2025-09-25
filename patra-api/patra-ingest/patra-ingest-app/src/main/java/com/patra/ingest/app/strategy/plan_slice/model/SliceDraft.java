package com.patra.ingest.app.strategy.plan_slice.model;

import com.patra.expr.Expr;
import java.time.Instant;
import java.util.Objects;

/**
 * 切片中间产物（未持久化）。
 */
public record SliceDraft(int sequence,
                         String sliceSignatureSeed,
                         String sliceSpecJson,
                         Expr sliceExpr,
                         String sliceExprJson,
                         String sliceExprHash,
                         Instant windowFrom,
                         Instant windowTo) {
    public SliceDraft {
        Objects.requireNonNull(sliceSignatureSeed, "sliceSignatureSeed不能为空");
        Objects.requireNonNull(sliceSpecJson, "sliceSpecJson不能为空");
        Objects.requireNonNull(sliceExprJson, "sliceExprJson不能为空");
        Objects.requireNonNull(sliceExprHash, "sliceExprHash不能为空");
    }
}
