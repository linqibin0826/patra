package com.patra.ingest.domain.policy;

import java.time.Duration;

/**
 * Pure domain policy implementing exponential backoff.
 */
public class RelayRetryPolicy {

    private final Duration base;
    private final double multiplier;
    private final Duration max;

    public RelayRetryPolicy(Duration base, double multiplier, Duration max) {
        if (base == null || base.isZero() || base.isNegative()) {
            throw new IllegalArgumentException("base backoff must be positive");
        }
        if (max == null || max.isZero() || max.isNegative()) {
            throw new IllegalArgumentException("max backoff must be positive");
        }
        if (multiplier < 1.0d) {
            throw new IllegalArgumentException("multiplier must not be less than 1");
        }
        this.base = base;
        this.multiplier = multiplier;
        this.max = max;
    }

    /**
     * Compute the backoff delay after the given attempt (attempt numbers start at 1).
     */
    public Duration computeDelay(int attempt) {
        if (attempt <= 1) {
            return clamp(base);
        }
        double factor = Math.pow(multiplier, attempt - 1);
        double scaledMillis = base.toMillis() * factor;
        if (Double.isInfinite(scaledMillis) || scaledMillis > Long.MAX_VALUE) {
            return max;
        }
        long millis = Math.max(base.toMillis(), (long) scaledMillis);
        return clamp(Duration.ofMillis(millis));
    }

    private Duration clamp(Duration candidate) {
        if (candidate.compareTo(max) > 0) {
            return max;
        }
        return candidate;
    }
}
