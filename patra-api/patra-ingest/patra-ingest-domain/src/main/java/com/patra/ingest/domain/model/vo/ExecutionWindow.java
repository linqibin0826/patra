package com.patra.ingest.domain.model.vo;

import java.time.Instant;

/**
 * 执行时间窗口值对象（半开区间语义）。
 * <p>语义：描述一次计划 / 任务 / 切片的时间范围，通常对应增量推进的边界。</p>
 * <ul>
 *   <li>windowFrom：包含（inclusive）</li>
 *   <li>windowTo：不包含（exclusive）</li>
 *   <li>null 表示无边界（如首次全量）</li>
 * </ul>
 * 不变式：若两端均不为空，windowTo >= windowFrom。
 */
public record ExecutionWindow(Instant windowFrom, Instant windowTo) {

    public ExecutionWindow {
        if (windowFrom != null && windowTo != null && windowTo.isBefore(windowFrom)) {
            throw new IllegalArgumentException("windowTo 不能早于 windowFrom");
        }
    }

    /**
     * 构建一个“无边界”窗口（表示不限制时间范围）。
     */
    public static ExecutionWindow empty() {
        return new ExecutionWindow(null, null);
    }

    /**
     * 是否定义了任一边界。
     *
     * @return true 表示至少存在一个非 null 边界
     */
    public boolean defined() {
        return windowFrom != null || windowTo != null;
    }
}
