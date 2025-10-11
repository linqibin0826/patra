package com.patra.ingest.domain.model.vo;

/**
 * Statistics for a single batch execution.
 * <p>{@code recordCount}: number of records processed (caller guarantees non-negative).</p>
 */
public record BatchStats(int recordCount) {
    /**
     * Convenience factory method.
     */
    public static BatchStats of(int count) {
        return new BatchStats(count);
    }
}
