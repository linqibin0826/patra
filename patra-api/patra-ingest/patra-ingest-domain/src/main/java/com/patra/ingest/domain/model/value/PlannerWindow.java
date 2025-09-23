package com.patra.ingest.domain.model.value;

import java.time.Instant;

/**
 * 计划窗口（Value Object · Domain）。
 * <p>统一采用 UTC 半开区间语义 [from, to)。from/to 为 null 代表“全量 / 无界”。</p>
 * <p>典型用法：
 * <ul>
 *   <li>{@link #full()} → 返回全量窗口（用于全量刷新 / 无限制抓取）；</li>
 *   <li>new PlannerWindow(from, to) → 返回具体时间片；</li>
 *   <li>{@link #isFull()} 判断是否为全量；</li>
 *   <li>{@link #isEmpty()} 校验逻辑是否构造出非正向窗口。</li>
 * </ul></p>
 */
public record PlannerWindow(Instant from, Instant to) {
    public PlannerWindow {
        if (from != null && to != null && !from.isBefore(to)) {
            throw new IllegalArgumentException("窗口起始时间必须早于结束时间");
        }
    }

    /**
     * 是否是“空”窗口（from>=to 的非法逻辑情况）。
     */
    public boolean isEmpty() {
        return from != null && to != null && !from.isBefore(to);
    }

    /**
     * 是否为全量窗口（null-null 表示无限制）。
     */
    public boolean isFull() {
        return from == null && to == null;
    }

    /**
     * 全量窗口工厂：返回一个无界窗口（from=null, to=null）。
     * @return 全量 PlannerWindow
     */
    public static PlannerWindow full() {
        return new PlannerWindow(null, null);
    }
}
