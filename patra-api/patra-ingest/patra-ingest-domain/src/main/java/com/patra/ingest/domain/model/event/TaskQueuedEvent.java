package com.patra.ingest.domain.model.event;

import com.patra.common.domain.DomainEvent;

import java.time.Instant;

/**
 * 任务入队领域事件。
 */
public record TaskQueuedEvent(
        Long taskId,
        Long planId,
        Long sliceId,
        Long scheduleInstanceId,
        String provenanceCode,
        String operationCode,
        String idempotentKey,
        String paramsJson,
        Integer priority,
        Instant scheduledAt,
        Instant occurredAt
) implements DomainEvent {

    public TaskQueuedEvent {
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }

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
