package com.patra.ingest.domain.model.value;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Outbox Relay 执行计划。
 */
public record RelayPlan(
        String channel,
        Instant triggeredAt,
        int batchSize,
        Duration leaseDuration,
        int maxAttempts,
        Duration initialBackoff,
        double backoffMultiplier,
        Duration maxBackoff,
        String leaseOwner
) {
    public RelayPlan {
        Objects.requireNonNull(channel, "channel 必填");
        Objects.requireNonNull(triggeredAt, "triggeredAt 必填");
        Objects.requireNonNull(leaseDuration, "leaseDuration 必填");
        Objects.requireNonNull(initialBackoff, "initialBackoff 必填");
        Objects.requireNonNull(maxBackoff, "maxBackoff 必填");
        Objects.requireNonNull(leaseOwner, "leaseOwner 必填");
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize 必须为正数");
        }
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts 必须为正数");
        }
        if (backoffMultiplier < 1.0d) {
            throw new IllegalArgumentException("backoffMultiplier 不能小于 1");
        }
    }

    public Instant leaseExpireAt() {
        return triggeredAt.plus(leaseDuration);
    }
}
