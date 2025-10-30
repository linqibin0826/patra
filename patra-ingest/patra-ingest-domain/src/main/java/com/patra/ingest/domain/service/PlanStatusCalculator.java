package com.patra.ingest.domain.service;

import com.patra.ingest.domain.model.enums.PlanStatus;
import com.patra.ingest.domain.model.enums.SliceStatus;
import java.util.List;

/**
 * Domain Service for calculating Plan status based on its child Slices.
 *
 * <p>This is a stateless pure function that encapsulates business rules for Plan status
 * aggregation.
 *
 * <p>Aggregation Rules (after refactoring):
 *
 * <ul>
 *   <li>If any Slice is PENDING or ASSIGNED → Plan remains at current status (not all done yet)
 *   <li>If all Slices are FINISHED → Plan is ARCHIVED (lifecycle closed)
 *   <li>If no Slices exist → Plan remains at current status (edge case)
 * </ul>
 *
 * <p><b>Note:</b> Plan status only reflects its own lifecycle. Execution results (partial/failed)
 * should be queried by aggregating Task status from the database.
 *
 * <p>This service only handles status transitions from READY onwards. The DRAFT → SLICING → READY
 * transitions are handled by the plan assembly process.
 */
public final class PlanStatusCalculator {

  private PlanStatusCalculator() {
    // Utility class, prevent instantiation
  }

  /**
   * Calculates the Plan status based on the statuses of all its child Slices.
   *
   * <p>Precondition: Plan must be in READY status or later. This method does not handle DRAFT →
   * SLICING → READY transitions.
   *
   * @param sliceStatuses list of slice statuses (must not be null, but can be empty)
   * @param currentPlanStatus current status of the plan
   * @return the aggregated Plan status
   * @throws IllegalArgumentException if sliceStatuses is null
   */
  public static PlanStatus calculate(
      List<SliceStatus> sliceStatuses, PlanStatus currentPlanStatus) {
    if (sliceStatuses == null) {
      throw new IllegalArgumentException("Slice statuses list cannot be null");
    }

    // Edge case: no slices exist (should not happen in normal flow)
    if (sliceStatuses.isEmpty()) {
      return currentPlanStatus; // Keep current status
    }

    // Check if any slice is still in progress
    boolean hasInProgress =
        sliceStatuses.stream().anyMatch(s -> s == SliceStatus.PENDING || s == SliceStatus.ASSIGNED);
    if (hasInProgress) {
      return currentPlanStatus; // Keep current status, not all slices are done
    }

    // All slices are FINISHED (terminal state)
    boolean allFinished = sliceStatuses.stream().allMatch(s -> s == SliceStatus.FINISHED);
    if (allFinished) {
      return PlanStatus.ARCHIVED; // Lifecycle closed
    }

    // Fallback: keep current status
    return currentPlanStatus;
  }

  /**
   * Checks if a Slice status is a terminal state (no further transitions expected).
   *
   * @param status slice status
   * @return true if terminal, false otherwise
   */
  public static boolean isTerminal(SliceStatus status) {
    return status == SliceStatus.FINISHED;
  }
}
