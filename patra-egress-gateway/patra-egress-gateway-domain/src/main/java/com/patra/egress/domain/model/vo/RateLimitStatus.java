package com.patra.egress.domain.model.vo;

import java.time.Duration;

/**
 * Value object describing the gateway's current rate limit state, including external provider
 * hints.
 *
 * @param limit configured gateway quota
 * @param remaining remaining quota for the current window
 * @param resetAfter duration until the quota resets
 * @param externalInfo optional rate limit metadata returned by the provider
 * @author linqibin
 * @since 0.1.0
 */
public record RateLimitStatus(
    int limit, int remaining, Duration resetAfter, ExternalRateLimitInfo externalInfo) {
  /** Canonical constructor that validates the integrity of the rate limit metrics. */
  public RateLimitStatus {
    if (limit < 0) {
      throw new IllegalArgumentException("Limit cannot be negative");
    }
    if (remaining < 0) {
      throw new IllegalArgumentException("Remaining cannot be negative");
    }
    if (resetAfter == null || resetAfter.isNegative()) {
      throw new IllegalArgumentException("ResetAfter must be non-null and non-negative");
    }
  }

  /**
   * Determine whether the gateway has exhausted the configured quota.
   *
   * @return {@code true} when no remaining tokens are available; {@code false} otherwise
   */
  public boolean isLimited() {
    return remaining <= 0;
  }
}
