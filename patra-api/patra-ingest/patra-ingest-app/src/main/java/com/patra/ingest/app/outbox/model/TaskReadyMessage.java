package com.patra.ingest.app.outbox.model;

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
            String params,
            String planKey,
            Instant planWindowFrom,
            Instant planWindowTo,
            String planSliceStrategy,
            String planSliceParams
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
