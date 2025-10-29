package com.patra.ingest.domain.service;

import com.patra.ingest.domain.model.enums.SliceStatus;
import com.patra.ingest.domain.model.enums.TaskStatus;
import java.util.List;

/**
 * Domain Service for calculating Slice status based on its child Tasks.
 *
 * <p>This is a stateless pure function that encapsulates business rules for Slice status
 * aggregation.
 *
 * <p>Aggregation Rules:
 *
 * <ul>
 *   <li>If any Task is QUEUED/RUNNING → Slice is EXECUTING (tasks still in progress)
 *   <li>If any Task is CURSOR_PENDING → Slice is EXECUTING (waiting for cursor retry)
 *   <li>If all Tasks are SUCCEEDED → Slice is SUCCEEDED
 *   <li>If all Tasks are FAILED or CANCELLED → Slice is FAILED
 *   <li>If Tasks are mixed (some succeeded, some failed) → Slice is PARTIAL
 *   <li>If no Tasks exist → Slice remains PENDING (edge case, should not happen)
 * </ul>
 */
public final class SliceStatusCalculator {

  private SliceStatusCalculator() {
    // Utility class, prevent instantiation
  }

  /**
   * Calculates the Slice status based on the statuses of all its child Tasks.
   *
   * @param taskStatuses list of task statuses (must not be null, but can be empty)
   * @return the aggregated Slice status
   * @throws IllegalArgumentException if taskStatuses is null
   */
  public static SliceStatus calculate(List<TaskStatus> taskStatuses) {
    if (taskStatuses == null) {
      throw new IllegalArgumentException("Task statuses list cannot be null");
    }

    // Edge case: no tasks exist (should not happen in normal flow)
    if (taskStatuses.isEmpty()) {
      return SliceStatus.PENDING;
    }

    // Check if any task is still in progress
    boolean hasRunning =
        taskStatuses.stream().anyMatch(s -> s == TaskStatus.QUEUED || s == TaskStatus.RUNNING);
    if (hasRunning) {
      return SliceStatus.EXECUTING;
    }

    // Check if any task is waiting for cursor retry
    boolean hasCursorPending = taskStatuses.stream().anyMatch(s -> s == TaskStatus.CURSOR_PENDING);
    if (hasCursorPending) {
      return SliceStatus.EXECUTING; // Treat CURSOR_PENDING as temporary failure, still executing
    }

    // Count terminal states
    long succeededCount = taskStatuses.stream().filter(s -> s == TaskStatus.SUCCEEDED).count();
    long failedCount =
        taskStatuses.stream()
            .filter(s -> s == TaskStatus.FAILED || s == TaskStatus.CANCELLED)
            .count();
    long partialCount = taskStatuses.stream().filter(s -> s == TaskStatus.PARTIAL).count();

    // All tasks succeeded
    if (succeededCount == taskStatuses.size()) {
      return SliceStatus.SUCCEEDED;
    }

    // All tasks failed or cancelled
    if (failedCount == taskStatuses.size()) {
      return SliceStatus.FAILED;
    }

    // Mixed results (some succeeded, some failed/partial)
    // Or any task is PARTIAL
    if (succeededCount > 0 || partialCount > 0) {
      return SliceStatus.PARTIAL;
    }

    // Fallback: if we get here, something unexpected happened
    return SliceStatus.EXECUTING;
  }

  /**
   * Checks if a Task status is a terminal state (no further transitions expected).
   *
   * @param status task status
   * @return true if terminal, false otherwise
   */
  public static boolean isTerminal(TaskStatus status) {
    return status == TaskStatus.SUCCEEDED
        || status == TaskStatus.FAILED
        || status == TaskStatus.CANCELLED
        || status == TaskStatus.PARTIAL;
  }
}
