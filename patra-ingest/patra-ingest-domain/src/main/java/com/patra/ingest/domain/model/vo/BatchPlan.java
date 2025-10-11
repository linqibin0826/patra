package com.patra.ingest.domain.model.vo;

import java.util.List;

/**
 * Value object representing a batch planning outcome.
 * <p>Encapsulates the batches produced by the planner along with the total count and limit flags.</p>
 * <p>Invariants:
 * <ul>
 *   <li>{@code batches} must not be {@code null} (but may be empty).</li>
 *   <li>{@code totalBatches} must be greater than or equal to zero.</li>
 * </ul>
 * </p>
 *
 * @param batches      batch list
 * @param totalBatches total number of batches
 * @param exceedsLimit whether the batch limit has been exceeded
 * @author linqibin
 * @since 0.1.0
 */
public record BatchPlan(
    List<Batch> batches,
    int totalBatches,
    boolean exceedsLimit
) {
    public BatchPlan {
        if (batches == null) {
            throw new IllegalArgumentException("batches must not be null");
        }
        if (totalBatches < 0) {
            throw new IllegalArgumentException("totalBatches must not be negative");
        }
    }

    /**
     * Create an empty batch plan.
     */
    public static BatchPlan empty() {
        return new BatchPlan(List.of(), 0, false);
    }

    /**
     * Create a plan containing a single batch.
     */
    public static BatchPlan single(Batch batch) {
        return new BatchPlan(List.of(batch), 1, false);
    }

    /**
     * Returns {@code true} when the plan contains at least one batch.
     */
    public boolean hasBatches() {
        return !batches.isEmpty();
    }
}
