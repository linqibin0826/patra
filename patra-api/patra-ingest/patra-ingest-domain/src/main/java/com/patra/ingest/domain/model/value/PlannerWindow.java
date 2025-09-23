package com.patra.ingest.domain.model.value;

import java.time.Instant;

/**
 * 计划窗口：统一采用 UTC 半开区间。
 */
public record PlannerWindow(Instant from, Instant to) {
    public PlannerWindow {
        if (from != null && to != null && !from.isBefore(to)) {
            throw new IllegalArgumentException("窗口起始时间必须早于结束时间");
        }
    }

    public boolean isEmpty() {
        return from != null && to != null && !from.isBefore(to);
    }
}
