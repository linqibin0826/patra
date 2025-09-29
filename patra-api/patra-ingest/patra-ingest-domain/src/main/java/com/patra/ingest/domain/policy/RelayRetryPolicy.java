package com.patra.ingest.domain.policy;

import java.time.Duration;

/**
 * 指数退避策略（纯领域策略）。
 */
public class RelayRetryPolicy {

    private final Duration base;
    private final double multiplier;
    private final Duration max;

    public RelayRetryPolicy(Duration base, double multiplier, Duration max) {
        if (base == null || base.isZero() || base.isNegative()) {
            throw new IllegalArgumentException("base backoff 必须为正值");
        }
        if (max == null || max.isZero() || max.isNegative()) {
            throw new IllegalArgumentException("max backoff 必须为正值");
        }
        if (multiplier < 1.0d) {
            throw new IllegalArgumentException("multiplier 不能小于 1");
        }
        this.base = base;
        this.multiplier = multiplier;
        this.max = max;
    }

    /**
     * 计算第 {@code attempt} 次失败后的退避时间（attempt 从 1 开始）。
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
