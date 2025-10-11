package com.patra.ingest.domain.model.vo;

/**
 * Aggregated statistics produced during a task run.
 * <p>Tracks raw records fetched, successfully upserted records, failures, and batch/page counts.</p>
 * Invariant: all fields are non-negative longs (callers must respect this).
 */
public record RunStats(long fetched, long upserted, long failed, long pages) {
    /**
     * Create an empty statistics record (all counters zero).
     */
    public static RunStats empty() {
        return new RunStats(0, 0, 0, 0);
    }

    /**
     * Combine this statistics snapshot with another immutable delta.
     *
     * @param delta incremental statistics to add
     * @return new aggregated statistics instance
     */
    public RunStats add(RunStats delta) {
        return new RunStats(fetched + delta.fetched, upserted + delta.upserted, failed + delta.failed, pages + delta.pages);
    }
}
