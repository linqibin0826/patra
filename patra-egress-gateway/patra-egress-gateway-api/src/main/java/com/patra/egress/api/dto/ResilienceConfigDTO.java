package com.patra.egress.api.dto;

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
    Long timeoutSeconds,
    Integer maxRetries,
    Long retryBackoffSeconds,
    Integer rateLimit,
    Integer circuitBreakerThreshold,
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
