package com.patra.ingest.domain.service;

import com.patra.ingest.domain.model.enums.SliceStatus;
import com.patra.ingest.domain.model.enums.TaskStatus;

/**
 * Domain Service for calculating Slice status based on its associated Task (enforces 1:1
 * relationship).
 *
 * <p>This is a stateless pure function that directly maps Task status to Slice status.
 *
 * <p>After refactoring, Slice:Task is a 1:1 relationship, so no aggregation is needed. The mapping
 * rules are:
 *
 * <ul>
 *   <li>TaskStatus.PENDING or QUEUED → SliceStatus.PENDING (awaiting Task generation/execution)
 *   <li>TaskStatus.RUNNING → SliceStatus.ASSIGNED (Task in progress)
 *   <li>TaskStatus.SUCCEEDED or FAILED → SliceStatus.FINISHED (Task reached terminal state)
 * </ul>
 *
 * <p><b>Note:</b> Slice no longer distinguishes success/failure. Query the Task directly for
 * execution results.
 */
public final class SliceStatusCalculator {

  private SliceStatusCalculator() {
    // Utility class, prevent instantiation
  }

  /**
   * Calculates the Slice status based on its associated Task status (1:1 mapping).
   *
   * @param taskStatus the status of the single associated Task (must not be null)
   * @return the corresponding Slice status
   * @throws IllegalArgumentException if taskStatus is null
   */
  public static SliceStatus calculate(TaskStatus taskStatus) {
    if (taskStatus == null) {
      throw new IllegalArgumentException("Task status cannot be null");
    }

    return switch (taskStatus) {
      case PENDING, QUEUED -> SliceStatus.PENDING;
      case RUNNING -> SliceStatus.ASSIGNED;
      case SUCCEEDED, FAILED -> SliceStatus.FINISHED;
    };
  }

  /**
   * Checks if a Task status is a terminal state (no further transitions expected).
   *
   * @param status task status
   * @return true if terminal, false otherwise
   */
  public static boolean isTerminal(TaskStatus status) {
    return status == TaskStatus.SUCCEEDED || status == TaskStatus.FAILED;
  }
}
