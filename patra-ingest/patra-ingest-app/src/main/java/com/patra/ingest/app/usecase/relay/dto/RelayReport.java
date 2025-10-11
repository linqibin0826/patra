package com.patra.ingest.app.usecase.relay.dto;

import com.patra.common.messaging.ChannelKey;

/**
 * Aggregated metrics for a single relay batch.
 * <p>Field semantics:
 * <ul>
 *   <li>{@code channel}: Outbox channel processed in this run.</li>
 *   <li>{@code fetched}: total messages attempted in the batch (including those with lease misses).</li>
 *   <li>{@code published}: messages successfully published and marked complete.</li>
 *   <li>{@code retried}: messages deferred for retry.</li>
 *   <li>{@code failed}: messages marked as permanently failed.</li>
 *   <li>{@code leaseMissed}: messages skipped because another instance acquired the lease.</li>
 * </ul>
 * Usage: logging, scheduler dashboards, and metric aggregation.
 */
public record RelayReport(
        ChannelKey channel,
        int fetched,
        int published,
        int retried,
        int failed,
        int leaseMissed
) {
    /** Empty report used when the feature is disabled. */
    public static RelayReport empty(ChannelKey channel) {
        return new RelayReport(channel, 0, 0, 0, 0, 0);
    }
}
