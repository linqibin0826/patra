package com.patra.egress.api.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

/**
 * Resilience configuration DTO
 *
 * <p>Allows callers to override system-level resilience settings
 * (timeout, retries, rate limit, circuit breaker) for specific requests.
 * All overrides are capped at system maximum values.</p>
 *
 * @param timeoutSeconds timeout in seconds (must be positive)
 * @param maxRetries maximum retry attempts (cannot be negative)
 * @param retryBackoffSeconds retry backoff delay in seconds (cannot be negative)
 * @param rateLimit rate limit (requests per second, must be positive)
 * @param circuitBreakerThreshold circuit breaker failure threshold (must be positive)
 * @param circuitBreakerWindowSeconds circuit breaker time window in seconds (must be positive)
 * @param responseHeaderWhitelist whitelist of response headers to include (optional)
 * @author linqibin
 * @since 0.1.0
 */
public record ResilienceConfigDTO(
    @Positive(message = "Timeout must be positive")
    Long timeoutSeconds,

    @PositiveOrZero(message = "MaxRetries cannot be negative")
    Integer maxRetries,

    @PositiveOrZero(message = "RetryBackoff cannot be negative")
    Long retryBackoffSeconds,

    @Positive(message = "RateLimit must be positive")
    Integer rateLimit,

    @Positive(message = "CircuitBreakerThreshold must be positive")
    Integer circuitBreakerThreshold,

    @Positive(message = "CircuitBreakerWindow must be positive")
    Long circuitBreakerWindowSeconds,

    List<String> responseHeaderWhitelist
) {
    /**
     * Compact constructor for validation
     */
    public ResilienceConfigDTO {
        // 创建不可变副本
        responseHeaderWhitelist = responseHeaderWhitelist != null
            ? List.copyOf(responseHeaderWhitelist)
            : null;
    }
}
