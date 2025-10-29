package com.patra.ingest.domain.event;

import com.patra.common.domain.DomainEvent;
import java.time.Instant;

/**
 * Domain event emitted when a Slice's status changes due to Task completion.
 *
 * <p>Trigger: fired after a Slice's status is recomputed based on the states of all its child
 * Tasks.
 *
 * <p>Usage:
 *
 * <ul>
 *   <li>Aggregation: triggers recomputation of parent Plan status based on all child Slices.
 *   <li>Metrics: measure slice completion rate, success rate, and failure rate by provenance.
 *   <li>Audit: trace slice lifecycle from creation to completion.
 *   <li>Monitoring: alert on high slice failure rates or stuck slices.
 * </ul>
 *
 * <p>Idempotency: {@code sliceId} + {@code newStatus} act as the composite key. Handlers must check
 * if the status has already been processed.
 *
 * <p>Event Chain: TaskCompletedEvent → SliceStatusChangedEvent → PlanAggregate status update.
 */
public record SliceStatusChangedEvent(
    /* Primary identifier of the slice. */
    Long sliceId,
    /* Identifier of the owning plan. */
    Long planId,
    /* Previous status of the slice before the change. */
    String oldStatus,
    /* New status of the slice after aggregation (EXECUTING, SUCCEEDED, FAILED, PARTIAL). */
    String newStatus,
    /* Timestamp when the event occurred. */
    Instant occurredAt)
    implements DomainEvent {

  public SliceStatusChangedEvent {
    // Ensure the event timestamp is always populated.
    occurredAt = occurredAt == null ? Instant.now() : occurredAt;
  }

  /**
   * Factory method that creates the event with auto-populated {@code occurredAt}.
   *
   * @param sliceId slice identifier
   * @param planId plan identifier
   * @param oldStatus previous status
   * @param newStatus new status
   * @return event instance
   */
  public static SliceStatusChangedEvent of(
      Long sliceId, Long planId, String oldStatus, String newStatus) {
    return new SliceStatusChangedEvent(sliceId, planId, oldStatus, newStatus, Instant.now());
  }
}
