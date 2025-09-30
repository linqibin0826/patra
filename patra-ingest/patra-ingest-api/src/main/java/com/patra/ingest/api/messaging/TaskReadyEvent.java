package com.patra.ingest.api.messaging;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * 采集任务准备就绪事件（API 契约）。
 * <p>
 * 对外暴露的事件载荷，消费方可依赖此 DTO 进行反序列化。
 * </p>
 *
 * <p><b>设计原则</b>：
 * <ul>
 *   <li>不可变（使用 @Value）</li>
 *   <li>字段向后兼容（新增字段设置默认值或 Optional）</li>
 *   <li>避免暴露内部领域模型细节</li>
 * </ul>
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Value
@Builder
public class TaskReadyEvent {
    /**
     * 采集计划 ID
     */
    Long planId;

    /**
     * 批次 ID
     */
    Long batchId;

    /**
     * 数据源代码（如 PUBMED / EPMC）
     */
    String sourceCode;

    /**
     * 任务调度时间
     */
    Instant scheduledAt;

    /**
     * 任务优先级（可选，默认 0）
     */
    @Builder.Default
    Integer priority = 0;
}
