package com.patra.ingest.app.model;

import java.util.Objects;

/**
 * 持久化到 Plan 聚合的表达式最小快照（hash + json）。
 */
public record PlanExprSnapshot(String hash, String json) {
    public PlanExprSnapshot {
        Objects.requireNonNull(hash, "hash不能为空");
        json = json == null ? "{}" : json;
    }
}
