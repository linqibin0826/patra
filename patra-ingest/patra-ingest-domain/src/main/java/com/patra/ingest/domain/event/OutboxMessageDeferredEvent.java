package com.patra.ingest.domain.event;

import java.time.Instant;

/**
 * Domain event that records a scheduled retry for an outbox message.
 *
 * <p>Trigger: emitted when publishing fails but is classified as retryable (threshold not exceeded and the
 * error is recoverable) so that the next retry plan is captured.</p>
 * <p>Usage:
 * <ul>
 *   <li>Scheduling: observe retry backlog trends to tune backoff strategies.</li>
 *   <li>Pipeline diagnostics: analyze error codes and retry counts to assess downstream stability.</li>
 * </ul>
 * </p>
 */
public record OutboxMessageDeferredEvent(
        /** Identifier of the message scheduled for replay. */
        Long messageId,
        /** Logical outbox channel. */
        String channel,
        /** Retry count that will be used for the next attempt (current failures + 1). */
        int nextRetryCount,
        /** Planned retry timestamp. */
        Instant nextRetryAt,
        /** Error code produced by the last failure. */
        String errorCode,
        /** Summary message for the failure. */
        String errorMessage,
        /** Timestamp when the event was emitted. */
        Instant occurredAt
) implements OutboxRelayDomainEvent {
}
