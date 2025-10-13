package com.patra.ingest.domain.event;

import java.time.Instant;

/**
 * Domain event emitted when an outbox message is successfully published.
 *
 * <p>Trigger: fired immediately after the outbox record is delivered to the message broker and
 * marked as {@code PUBLISHED}.
 *
 * <p>Usage:
 *
 * <ul>
 *   <li>Metrics: measure success rates and partition distribution for each channel.
 *   <li>Audit: correlate {@code messageId} with {@code brokerMessageId} to trace broker flows.
 *   <li>Downstream: optional consumers can build second-stage fan-out or populate caches.
 * </ul>
 *
 * <p>Idempotency: each {@code messageId} should emit this event once; listeners that require
 * additional idempotency can reuse {@code messageId} as their key.
 */
public record OutboxMessagePublishedEvent(
    /** Primary identifier of the outbox record. */
    Long messageId,
    /** Logical channel (topic/stream) used during publishing. */
    String channel,
    /** Partition routing key, potentially hashed by the broker. */
    String partitionKey,
    /** Broker-provided message identifier for cross-system tracking. */
    String brokerMessageId,
    /** UTC timestamp when the event occurred. */
    Instant occurredAt)
    implements OutboxRelayDomainEvent {}
