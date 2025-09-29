package com.patra.ingest.domain.model.event;

import com.patra.common.domain.DomainEvent;

import java.time.Instant;

/**
 * 任务入队领域事件。
 *
 * <p>触发：任务被成功创建并进入待执行队列（或持久化为可调度状态）后发布。</p>
 * <p>用途：
 * <ul>
 *   <li>指标：统计 provenance / operation 维度的任务新增速率。</li>
 *   <li>审计：追踪调度实例与切片到任务的映射链路。</li>
 *   <li>下游：可驱动实时监控面板刷新。</li>
 * </ul>
 * </p>
 * <p>幂等：以 {@code taskId} 为唯一键；重复发布视为异常，应在上游避免。</p>
 */
public record TaskQueuedEvent(
    /** 任务主键 ID。 */
        Long taskId,
    /** 所属计划 ID。 */
        Long planId,
    /** 所属切片 ID。 */
        Long sliceId,
    /** 调度实例 ID。 */
        Long scheduleInstanceId,
    /** 来源代码。 */
        String provenanceCode,
    /** 操作代码。 */
        String operationCode,
    /** 幂等键（去重语义）。 */
        String idempotentKey,
    /** 任务参数 JSON。 */
        String paramsJson,
    /** 优先级（数值越大优先级可能越高，视实现）。 */
        Integer priority,
    /** 计划调度执行时间。 */
        Instant scheduledAt,
    /** 事件发生时间。 */
        Instant occurredAt
) implements DomainEvent {

    public TaskQueuedEvent {
        // 保障事件发生时间非空，缺失则补当前时间
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }

    /**
     * 工厂方法：使用必需上下文创建事件，并自动填充 occurredAt。
     *
     * @param taskId 任务 ID
     * @param planId 计划 ID
     * @param sliceId 切片 ID
     * @param scheduleInstanceId 调度实例 ID
     * @param provenanceCode 来源代码
     * @param operationCode 操作代码
     * @param idempotentKey 幂等键
     * @param paramsJson 参数 JSON
     * @param priority 优先级
     * @param scheduledAt 计划执行时间
     * @return 事件实例
     */
    public static TaskQueuedEvent of(Long taskId,
                                     Long planId,
                                     Long sliceId,
                                     Long scheduleInstanceId,
                                     String provenanceCode,
                                     String operationCode,
                                     String idempotentKey,
                                     String paramsJson,
                                     Integer priority,
                                     Instant scheduledAt) {
        return new TaskQueuedEvent(
                taskId,
                planId,
                sliceId,
                scheduleInstanceId,
                provenanceCode,
                operationCode,
                idempotentKey,
                paramsJson,
                priority,
                scheduledAt,
                Instant.now());
    }
}
