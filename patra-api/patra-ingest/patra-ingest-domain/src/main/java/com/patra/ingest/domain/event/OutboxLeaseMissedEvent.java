package com.patra.ingest.domain.event;

import java.time.Instant;

/**
 * Domain event raised when an outbox message lease cannot be acquired.
 *
 * <p>Trigger: emitted after multiple relay instances contend for the same message lease and the
 * optimistic locking update fails. Useful for observing lease contention levels.
 *
 * <p>Usage:
 *
 * <ul>
 *   <li>Scaling decisions: repeated lease conflicts may indicate that concurrency is too high.
 *   <li>Hotspot analysis: aggregate by {@code channel + messageId} to surface hot messages.
 * </ul>
 */
public record OutboxLeaseMissedEvent(
    /** Identifier of the message that triggered the lease conflict. */
    Long messageId,
    /** Outbox channel associated with the message. */
    String channel,
    /** Lease owner requested by the current relay instance. */
    String requestedLeaseOwner,
    /** Lease owner already recorded in the database. */
    String currentLeaseOwner,
    /** Timestamp when the event occurred. */
    Instant occurredAt)
    implements OutboxRelayDomainEvent {}
