package com.patra.ingest.adapter.inbound.scheduler.param;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 基于来源调度任务的通用参数模型，对齐 XXL-Job 传入的 JSON 结构。
 * <p>字段均为可选：
 * <ul>
 *   <li>windowFrom/windowTo：时间窗口边界，ISO-8601 Instant 字符串。</li>
 *   <li>priority：调度优先级，忽略大小写的枚举名。</li>
 *   <li>step：切片步长，ISO-8601 Duration 字符串。</li>
 *   <li>schedulerLogId：调度日志标识，缺省时回退为 0。</li>
 *   <li>triggeredAt：触发时间戳，ISO-8601 Instant 字符串。</li>
 * </ul>
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProvenanceScheduleJobParam(
        String windowFrom,
        String windowTo,
        String priority,
        String step,
        String schedulerLogId,
        String triggeredAt
) {

    /**
     * 构造空参数实例，便于统一回退逻辑。
     *
     * @return 空参数对象
     */
    public static ProvenanceScheduleJobParam empty() {
        return new ProvenanceScheduleJobParam(null, null, null, null, null, null);
    }
}
