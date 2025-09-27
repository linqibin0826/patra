package com.patra.ingest.domain.model.vo;

import java.time.Instant;

/**
 * 单次运行覆盖的时间窗口。
 */
public record ExecutionWindow(Instant windowFrom, Instant windowTo) {

    public ExecutionWindow {
        if (windowFrom != null && windowTo != null && windowTo.isBefore(windowFrom)) {
            throw new IllegalArgumentException("windowTo 不能早于 windowFrom");
        }
    }

    public static ExecutionWindow empty() {
        return new ExecutionWindow(null, null);
    }

    public boolean defined() {
        return windowFrom != null || windowTo != null;
    }
}
