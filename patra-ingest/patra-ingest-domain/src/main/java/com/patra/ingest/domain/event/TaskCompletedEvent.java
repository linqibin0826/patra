package com.patra.ingest.domain.event;

import com.patra.common.domain.DomainEvent;
import java.time.Instant;

/**
 * Domain event emitted when a task completes execution (either successfully or with failure).
 *
 * <p>Trigger: fired after a task transitions to a terminal state (SUCCEEDED, FAILED, PARTIAL).
 *
 * <p>Usage:
 *
 * <ul>
 *   <li>Aggregation: triggers recomputation of parent Slice status based on all child Tasks.
 *   <li>Metrics: measure task completion rate, success rate, and failure rate by provenance and
 *       operation.
 *   <li>Audit: trace task lifecycle from creation to completion.
 *   <li>Monitoring: alert on high failure rates or stuck tasks.
 * </ul>
 *
 * <p>Idempotency: {@code taskId} acts as the unique key. Handlers must check if the status has
 * already been processed.
 *
 * <p>Event Chain: TaskCompletedEvent → SliceStatusChangedEvent → PlanAggregate status update.
 */
public record TaskCompletedEvent(
    /* Primary identifier of the completed task. */
    Long taskId,
    /* Identifier of the owning slice. */
    Long sliceId,
    /* Identifier of the owning plan. */
    Long planId,
    /* Final status of the task (SUCCEEDED, FAILED, CURSOR_PENDING, etc). */
    String status,
    /* Error code if task failed (null if succeeded). */
    String errorCode,
    /* Error message if task failed (null if succeeded). */
    String errorMessage,
    /* Timestamp when the task finished execution. */
    Instant finishedAt,
    /* Timestamp when the event occurred. */
    Instant occurredAt)
    implements DomainEvent {

  public TaskCompletedEvent {
    // Ensure the event timestamp is always populated.
    occurredAt = occurredAt == null ? Instant.now() : occurredAt;
  }

  /**
   * Factory method for successful task completion.
   *
   * @param taskId task identifier
   * @param sliceId slice identifier
   * @param planId plan identifier
   * @param status task status
   * @param finishedAt completion timestamp
   * @return event instance
   */
  public static TaskCompletedEvent of(
      Long taskId, Long sliceId, Long planId, String status, Instant finishedAt) {
    return new TaskCompletedEvent(
        taskId, sliceId, planId, status, null, null, finishedAt, Instant.now());
  }

  /**
   * Factory method for failed task completion with error details.
   *
   * @param taskId task identifier
   * @param sliceId slice identifier
   * @param planId plan identifier
   * @param status task status
   * @param errorCode error code
   * @param errorMessage error message
   * @param finishedAt completion timestamp
   * @return event instance
   */
  public static TaskCompletedEvent ofFailure(
      Long taskId,
      Long sliceId,
      Long planId,
      String status,
      String errorCode,
      String errorMessage,
      Instant finishedAt) {
    return new TaskCompletedEvent(
        taskId, sliceId, planId, status, errorCode, errorMessage, finishedAt, Instant.now());
  }
}
