package com.patra.ingest.app.outbox.dto;

/**
 * Relay 结果统计。
 */
public record OutboxRelayResult(
        int fetched,
        int succeeded,
        int retried,
        int dead,
        int skipped
) {
}
