package com.patra.ingest.app.usecase.plan.publisher;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.ingest.domain.outbox.OutboxPayload;

import java.time.Instant;

/**
 * Task Outbox message payload.
 * <p>Contains business data required by downstream task consumers.</p>
 *
 * @param taskId        Task identifier (must not be null)
 * @param planId        Plan identifier
 * @param sliceId       Slice identifier (null if not sliced)
 * @param provenance    Provenance code (data source identifier)
 * @param operation     Operation code (e.g., FETCH, PARSE)
 * @param idempotentKey Idempotent key for deduplication
 * @param priority      Task priority (null for default)
 * @param scheduledAt   Scheduled execution time (null for immediate)
 * @param params        Task parameters as JSON (null if no params)
 * @author linqibin
 * @since 0.1.0
 */
public record TaskPayload(
        Long taskId,
        Long planId,
        Long sliceId,
        String provenance,
        String operation,
        String idempotentKey,
        Integer priority,
        Instant scheduledAt,
        JsonNode params
) implements OutboxPayload {

    /**
     * Creates a TaskPayload with all fields.
     *
     * @param taskId        Task ID (required)
     * @param planId        Plan ID
     * @param sliceId       Slice ID (nullable)
     * @param provenance    Provenance code
     * @param operation     Operation code
     * @param idempotentKey Idempotent key
     * @param priority      Priority (nullable)
     * @param scheduledAt   Scheduled time (nullable)
     * @param params        Parameters JSON (nullable)
     */
    public TaskPayload {
        // Compact constructor - validation can be added here if needed
    }
}
