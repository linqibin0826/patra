package com.patra.ingest.adapter.inbound.scheduler.param;

/**
 * Outbox Relay 任务参数。
 */
public record OutboxRelayJobParam(
        String channel,
        Integer batchSize,
        String leaseDuration,
        Integer maxRetry,
        String retryBackoff
) {
}
