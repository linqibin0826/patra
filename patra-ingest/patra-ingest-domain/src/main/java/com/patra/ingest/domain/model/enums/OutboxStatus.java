package com.patra.ingest.domain.model.enums;

/**
 * Outbox message status representing the reliable delivery state machine.
 *
 * <ul>
 *   <li>PENDING: ready to be scanned for publishing
 *   <li>PUBLISHING: currently held by a lease during publish
 *   <li>PUBLISHED: successfully delivered downstream (broker id recorded)
 *   <li>FAILED: failed publish eligible for retry or eventual DEAD
 *   <li>DEAD: retries exhausted or manually quarantined
 * </ul>
 */
public enum OutboxStatus {
  /** Pending publish. */
  PENDING,
  /** Publishing in progress; lease prevents concurrency. */
  PUBLISHING,
  /** Successfully published. */
  PUBLISHED,
  /** Failed but still within retry strategy. */
  FAILED,
  /** Dead and no longer retried. */
  DEAD
}
