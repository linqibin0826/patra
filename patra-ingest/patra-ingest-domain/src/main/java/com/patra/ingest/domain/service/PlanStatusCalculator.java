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
 * <p>Aggregation Rules:
 *
 * <ul>
 *   <li>If any Slice is PENDING/EXECUTING → Plan remains at current status (not all done yet)
 *   <li>If all Slices are SUCCEEDED → Plan is COMPLETED
 *   <li>If all Slices are FAILED or CANCELLED → Plan is FAILED
 *   <li>If Slices are mixed (some succeeded, some failed/partial) → Plan is PARTIAL
 *   <li>If no Slices exist → Plan remains at current status (edge case)
 * </ul>
 *
 * <p>Note: This service only handles status transitions from READY onwards. The DRAFT → SLICING →
 * READY transitions are handled by the plan assembly process.
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
        sliceStatuses.stream()
            .anyMatch(s -> s == SliceStatus.PENDING || s == SliceStatus.EXECUTING);
    if (hasInProgress) {
      return currentPlanStatus; // Keep current status, not all slices are done
    }

    // All slices are in terminal states, aggregate the result
    long succeededCount = sliceStatuses.stream().filter(s -> s == SliceStatus.SUCCEEDED).count();
    long failedCount =
        sliceStatuses.stream()
            .filter(s -> s == SliceStatus.FAILED || s == SliceStatus.CANCELLED)
            .count();
    long partialCount = sliceStatuses.stream().filter(s -> s == SliceStatus.PARTIAL).count();

    // All slices succeeded
    if (succeededCount == sliceStatuses.size()) {
      return PlanStatus.COMPLETED;
    }

    // All slices failed or cancelled
    if (failedCount == sliceStatuses.size()) {
      return PlanStatus.FAILED;
    }

    // Mixed results (some succeeded, some failed/partial)
    // Or any slice is PARTIAL
    if (succeededCount > 0 || partialCount > 0) {
      return PlanStatus.PARTIAL;
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
    return status == SliceStatus.SUCCEEDED
        || status == SliceStatus.FAILED
        || status == SliceStatus.CANCELLED
        || status == SliceStatus.PARTIAL;
  }
}
