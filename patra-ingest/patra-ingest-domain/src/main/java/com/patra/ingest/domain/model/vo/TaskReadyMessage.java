package com.patra.ingest.domain.model.vo;

import java.time.Instant;

/**
 * Message payload for the {@code INGEST_TASK_READY} channel.
 *
 * @param payload message body
 * @param header message headers
 */
public record TaskReadyMessage(
        Payload payload,
        Header header
) {

    /**
     * Task message body.
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
     * Task-specific parameters required for execution.
     */
    public record TaskParams(
            Integer sliceNo
    ) {
    }

    /**
     * Plan slice parameters describing the applied slicing strategy.
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
