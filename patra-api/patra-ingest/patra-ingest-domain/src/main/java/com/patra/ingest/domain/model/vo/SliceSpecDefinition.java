package com.patra.ingest.domain.model.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * 切片规格定义（Slice Spec Definition）。
 * <p>
 * 用于解析 plan_slice 表中的 slice_spec JSON 字段，包含切片策略和窗口信息。
 * </p>
 * <ul>
 *   <li>strategy：切片策略（TIME/ID/TOKEN 等）</li>
 *   <li>window：时间窗口定义（仅当 strategy=TIME 时有效）</li>
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SliceSpecDefinition(
        String strategy, // TODO 枚举化
        WindowDefinition window
) {

    /**
     * 时间窗口定义。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WindowDefinition(
            Instant from,
            Instant to,
            BoundaryDefinition boundary,
            String timezone
    ) {
    }

    /**
     * 边界定义（CLOSED/OPEN）。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BoundaryDefinition(
            String from,
            String to
    ) {
    }

    /**
     * 判断是否为时间策略。
     *
     * @return true 表示策略为 TIME
     */
    public boolean isTimeStrategy() {
        return "TIME".equalsIgnoreCase(strategy);
    }

    /**
     * 提取执行窗口（仅当 strategy=TIME 时返回有效窗口）。
     *
     * @return ExecutionWindow 或 empty
     */
    public ExecutionWindow toExecutionWindow() {
        if (!isTimeStrategy() || window == null) {
            return ExecutionWindow.empty();
        }
        return new ExecutionWindow(window.from(), window.to());
    }
}
