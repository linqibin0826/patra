package com.patra.ingest.app.usecase.plan.publisher;

import com.patra.ingest.domain.outbox.OutboxPayload;

import java.util.Objects;

/**
 * Task Outbox message payload (simplified).
 * <p>
 * Contains only essential data required by downstream consumers.
 * All other business data (provenance, operation, params, etc.) should be queried
 * from the database using the taskId.
 * </p>
 *
 * @param taskId        Task identifier (required for context loading and lease acquisition)
 * @param idempotentKey Idempotent key (required for deduplication and idempotency check)
 * @author linqibin
 * @since 0.1.0
 */
public record TaskPayload(
        Long taskId,
        String idempotentKey
) implements OutboxPayload {

    /**
     * Compact constructor with validation.
     *
     * @param taskId        Task ID (must not be null)
     * @param idempotentKey Idempotent key (must not be null)
     */
    public TaskPayload {
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(idempotentKey, "idempotentKey must not be null");
    }
}
