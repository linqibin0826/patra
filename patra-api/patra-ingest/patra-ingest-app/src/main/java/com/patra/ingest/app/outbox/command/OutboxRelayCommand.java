package com.patra.ingest.app.outbox.command;

import java.time.Duration;
import java.time.Instant;

/**
 * Relay 调度命令。
 */
public record OutboxRelayCommand(
        String channel,
        Instant executeAt,
        int batchSize,
        Duration leaseDuration,
        int maxRetry,
        Duration retryBackoff,
        String leaseOwner
) {
}
