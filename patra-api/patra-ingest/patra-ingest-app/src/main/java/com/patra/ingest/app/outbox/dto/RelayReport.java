package com.patra.ingest.app.outbox.dto;

/**
 * Relay 执行统计结果。
 */
public record RelayReport(
        String channel,
        int fetched,
        int published,
        int retried,
        int failed,
        int leaseMissed
) {
    public static RelayReport empty(String channel) {
        return new RelayReport(channel, 0, 0, 0, 0, 0);
    }
}
