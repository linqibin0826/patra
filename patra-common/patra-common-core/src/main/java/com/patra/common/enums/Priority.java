package com.patra.common.enums;

/**
 * Task priority levels with associated queue values for scheduling.
 *
 * <p>Lower queue values indicate higher priority. Used by task scheduling and queueing systems to
 * determine execution order.
 */
public enum Priority {
  /** Highest priority with queue value 10. */
  HIGHEST(10),

  /** Higher priority with queue value 20. */
  HIGHER(20),

  /** High priority with queue value 30. */
  HIGH(30),

  /** Normal priority with queue value 50 (default). */
  NORMAL(50),

  /** Low priority with queue value 70. */
  LOW(70),

  /** Lower priority with queue value 80. */
  LOWER(80),

  /** Lowest priority with queue value 90. */
  LOWEST(90);

  private final int queueValue;

  Priority(int queueValue) {
    this.queueValue = queueValue;
  }

  /**
   * Returns the queue value for priority-based scheduling.
   *
   * @return queue value where lower numbers indicate higher priority
   */
  public int queueValue() {
    return queueValue;
  }
}
