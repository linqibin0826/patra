package com.patra.ingest.app.usecase.plan.publisher;

import com.patra.ingest.domain.outbox.OutboxHeaders;

import java.time.Instant;

/**
 * Task Outbox message headers.
 * <p>Contains metadata for tracing, correlation, and performance monitoring.</p>
 *
 * <h3>Header Categories</h3>
 * <ul>
 *   <li><b>Schedule Tracing</b>: scheduleInstanceId, scheduler, schedulerJobId</li>
 *   <li><b>Time Tracing</b>: triggeredAt, occurredAt (for latency analysis)</li>
 * </ul>
 *
 * @param scheduleInstanceId Schedule instance identifier for correlation
 * @param scheduler          Scheduler type (e.g., MANUAL, XXL_JOB)
 * @param schedulerJobId     Scheduler job identifier (null if not applicable)
 * @param triggeredAt        Schedule trigger timestamp
 * @param occurredAt         Event occurrence timestamp
 * @author linqibin
 * @since 0.1.0
 */
public record TaskHeaders(
        Long scheduleInstanceId,
        String scheduler,
        String schedulerJobId,
        Instant triggeredAt,
        Instant occurredAt
) implements OutboxHeaders {

    /**
     * Creates TaskHeaders with all fields.
     *
     * @param scheduleInstanceId Schedule instance ID (required)
     * @param scheduler          Scheduler name (required)
     * @param schedulerJobId     Scheduler job ID (nullable)
     * @param triggeredAt        Trigger timestamp (required)
     * @param occurredAt         Occurrence timestamp (required)
     */
    public TaskHeaders {
        // Compact constructor - validation can be added here if needed
    }
}
