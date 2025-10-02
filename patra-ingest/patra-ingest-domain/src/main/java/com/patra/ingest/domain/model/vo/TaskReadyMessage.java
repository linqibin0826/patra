package com.patra.ingest.domain.model.vo;

import java.time.Instant;

/**
 * ingest.task.ready 消息载体。
 *
 * @param payload 消息主体
 * @param header 消息头部
 */
public record TaskReadyMessage(
        Payload payload,
        Header header
) {

    /**
     * 任务消息主体。
     */
    public record Payload(
            Long taskId,
            Long planId,
            Long sliceId,
            String provenance,
            String operation,
            String idempotentKey,
            Integer priority,
            Instant scheduledAt,
            TaskParams params,
            String planKey,
            Instant planWindowFrom,
            Instant planWindowTo,
            String planSliceStrategy,
            PlanSliceParams planSliceParams
    ) {
    }

    /**
     * 任务参数。
     * <p>包含任务执行所需的具体参数。</p>
     */
    public record TaskParams(
            Integer sliceNo
    ) {
    }

    /**
     * 计划切片参数。
     * <p>描述切片策略的具体参数。</p>
     */
    public record PlanSliceParams(
            String strategy
    ) {
    }

    public record Header(
            Long scheduleInstanceId,
            String scheduler,
            Long schedulerJobId,
            Long schedulerLogId,
            String triggerType,
            Instant triggeredAt,
            Instant occurredAt,
            String planKey,
            String planOperation,
            String planEndpoint
    ) {
    }
}
