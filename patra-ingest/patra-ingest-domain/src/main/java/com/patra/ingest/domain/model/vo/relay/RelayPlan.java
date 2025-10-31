package com.patra.ingest.domain.model.vo.relay;

import com.patra.common.messaging.ChannelKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Execution plan for the outbox relay.
 *
 * <p>A {@code null} channel indicates that all channels should be processed.
 */
public record RelayPlan(
    ChannelKey channel,
    Instant triggeredAt,
    int batchSize,
    Duration leaseDuration,
    int maxAttempts,
    Duration initialBackoff,
    double backoffMultiplier,
    Duration maxBackoff,
    String leaseOwner) {
  public RelayPlan {
    // channel may be null to indicate "all channels"
    Objects.requireNonNull(triggeredAt, "triggeredAt must not be null");
    Objects.requireNonNull(leaseDuration, "leaseDuration must not be null");
    Objects.requireNonNull(initialBackoff, "initialBackoff must not be null");
    Objects.requireNonNull(maxBackoff, "maxBackoff must not be null");
    Objects.requireNonNull(leaseOwner, "leaseOwner must not be null");
    if (batchSize <= 0) {
      throw new IllegalArgumentException("batchSize must be positive");
    }
    if (maxAttempts <= 0) {
      throw new IllegalArgumentException("maxAttempts must be positive");
    }
    if (backoffMultiplier < 1.0d) {
      throw new IllegalArgumentException("backoffMultiplier must be at least 1");
    }
  }

  public Instant leaseExpireAt() {
    return triggeredAt.plus(leaseDuration);
  }
}
