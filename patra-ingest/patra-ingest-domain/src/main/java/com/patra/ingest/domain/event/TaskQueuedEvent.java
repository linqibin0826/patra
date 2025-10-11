package com.patra.ingest.domain.event;

import com.patra.common.domain.DomainEvent;

import java.time.Instant;

/**
 * Domain event emitted when a task enters the execution queue.
 *
 * <p>Trigger: fired after a task is successfully created and persisted in a schedulable state.</p>
 * <p>Usage:
 * <ul>
 *   <li>Metrics: measure task creation rate by provenance and operation dimensions.</li>
 *   <li>Audit: trace scheduling instances and slices through to concrete tasks.</li>
 *   <li>Downstream: update real-time monitoring dashboards.</li>
 * </ul>
 * </p>
 * <p>Idempotency: {@code taskId} acts as the unique key. Duplicate emissions indicate an upstream issue.</p>
 */
public record TaskQueuedEvent(
        /* Primary identifier of the task. */
        Long taskId,
        /* Identifier of the owning plan. */
        Long planId,
        /* Identifier of the owning slice. */
        Long sliceId,
        /* Scheduling instance identifier. */
        Long scheduleInstanceId,
        /* Provenance code. */
        String provenanceCode,
        /* Operation code. */
        String operationCode,
        /* Idempotency key for deduplication. */
        String idempotentKey,
        /* Task parameters serialized as JSON. */
        String paramsJson,
        /* Scheduling priority (higher numbers typically mean higher priority). */
        Integer priority,
        /* Planned execution timestamp. */
        Instant scheduledAt,
        /* Timestamp when the event occurred. */
        Instant occurredAt
) implements DomainEvent {

    public TaskQueuedEvent {
        // Ensure the event timestamp is always populated.
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }

    /**
     * Factory method that creates the event with mandatory context and auto-populates {@code occurredAt}.
     *
     * @param taskId             task identifier
     * @param planId             plan identifier
     * @param sliceId            slice identifier
     * @param scheduleInstanceId scheduling instance identifier
     * @param provenanceCode     provenance code
     * @param operationCode      operation code
     * @param idempotentKey      idempotency key
     * @param paramsJson         task parameters JSON
     * @param priority           scheduling priority
     * @param scheduledAt        planned execution timestamp
     * @return event instance
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
