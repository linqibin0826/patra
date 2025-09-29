package com.patra.ingest.app.outbox.command;

import java.time.Duration;
import java.time.Instant;

/**
 * 外部调度触发的 Relay 指令，可覆盖默认参数。
 */
public record OutboxRelayInstruction(
        String channel,
        Instant triggeredAt,
        Integer batchSize,
        Duration leaseDuration,
        Integer maxAttempts,
        Duration initialBackoff,
        String leaseOwner
) {
}
