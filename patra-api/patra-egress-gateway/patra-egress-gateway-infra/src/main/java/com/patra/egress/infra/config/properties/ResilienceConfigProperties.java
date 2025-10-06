package com.patra.egress.infra.config.properties;

import lombok.Data;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Resilience configuration properties for a single config set
 * Maps to individual resilience configuration (max or default)
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
public class ResilienceConfigProperties {

    /**
     * Request timeout duration
     * Default: 30s (for default), 60s (for max)
     */
    private Duration timeout = Duration.ofSeconds(30);

    /**
     * Maximum number of retry attempts
     * Default: 3 (for default), 5 (for max)
     */
    private int maxRetries = 3;

    /**
     * Initial backoff duration for retry
     * Default: 2s (for default), 10s (for max)
     */
    private Duration retryBackoff = Duration.ofSeconds(2);

    /**
     * Rate limit (requests per second) for this configuration
     * Default: 100 (for default), 1000 (for max)
     */
    private int rateLimit = 100;

    /**
     * Circuit breaker failure rate threshold (percentage)
     * Default: 50
     */
    private int circuitBreakerFailureThreshold = 50;

    /**
     * Circuit breaker wait duration in OPEN state
     * Default: 60s (for default), 30s (for max)
     */
    private Duration circuitBreakerWaitDuration = Duration.ofSeconds(60);

    /**
     * Whitelist of response headers to include in ResponseEnvelope
     * Default: Content-Type, X-RateLimit-*, Retry-After, ETag, etc.
     */
    private List<String> whitelistResponseHeaders = new ArrayList<>(List.of(
        "Content-Type",
        "X-RateLimit-Limit",
        "X-RateLimit-Remaining",
        "X-RateLimit-Reset",
        "Retry-After",
        "ETag",
        "Last-Modified"
    ));
}
