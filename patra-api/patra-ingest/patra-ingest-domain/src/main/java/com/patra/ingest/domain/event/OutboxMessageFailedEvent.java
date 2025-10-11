package com.patra.ingest.domain.event;

import java.time.Instant;

/**
 * Domain event emitted when a message is declared dead and further retries stop.
 *
 * <p>Trigger: fired after the retry limit is reached or a strategy determines the failure is unrecoverable
 * (for example, permanently incompatible payload format).</p>
 * <p>Usage:
 * <ul>
 *   <li>Alerting: aggregate by error code to locate hot failure patterns quickly.</li>
 *   <li>Compensation: drive manual or offline replay tools to inspect dead messages.</li>
 * </ul>
 * </p>
 */
public record OutboxMessageFailedEvent(
        /** Identifier of the outbox message. */
        Long messageId,
        /** Logical channel of the message. */
        String channel,
        /** Retry count that occurred before the final failure. */
        int retryCount,
        /** Error code associated with the terminal failure. */
        String errorCode,
        /** Summary message describing the terminal failure. */
        String errorMessage,
        /** Timestamp when this event occurred. */
        Instant occurredAt
) implements OutboxRelayDomainEvent {
}
