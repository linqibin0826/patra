package com.patra.ingest.domain.event;

import com.patra.common.domain.DomainEvent;

/**
 * Marker interface for outbox relay domain events.
 *
 * <p>Semantics: events produced during the outbox relay loop (fetch -> lease -> publish -> persist) and
 * intended for external consumers (monitoring, audit, asynchronous downstream) must implement this interface.
 * The common marker lets the event bus and listeners group or filter events conveniently.</p>
 * <p>Design guidelines:
 * <ul>
 *   <li>Event types should rely on immutable records or read-only fields so the payload remains stable.</li>
 *   <li>Field names should mirror the outbox table and publishing context to reduce interpretation effort.</li>
 *   <li>Every implementation must expose an {@code occurredAt} field; when missing, provide a default timestamp
 *   during construction.</li>
 * </ul>
 * </p>
 */
public interface OutboxRelayDomainEvent extends DomainEvent {
}
