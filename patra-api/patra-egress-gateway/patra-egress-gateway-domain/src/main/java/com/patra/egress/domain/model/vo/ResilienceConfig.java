package com.patra.egress.domain.model.vo;

import java.time.Duration;
import java.util.List;

/**
 * Resilience configuration value object that captures the guardrails applied to outbound calls.
 *
 * @param timeout                  request timeout
 * @param maxRetries               maximum number of retries
 * @param retryBackoff             backoff interval between retries
 * @param rateLimit                allowed requests per second
 * @param circuitBreakerThreshold  number of failures that trigger circuit breaking
 * @param circuitBreakerWindow     evaluation window for the circuit breaker
 * @param responseHeaderWhitelist  headers that can flow back to the caller
 * @author linqibin
 * @since 0.1.0
 */
public record ResilienceConfig(
    Duration timeout,
    int maxRetries,
    Duration retryBackoff,
    int rateLimit,
    int circuitBreakerThreshold,
    Duration circuitBreakerWindow,
    List<String> responseHeaderWhitelist
) {
    /**
     * Canonical constructor that guarantees immutability for internal collections.
     */
    public ResilienceConfig {
        // Create an immutable defensive copy of the whitelist to prevent accidental mutation.
        responseHeaderWhitelist = responseHeaderWhitelist != null 
            ? List.copyOf(responseHeaderWhitelist) 
            : List.of();
    }
    
    /**
     * Validate the configuration to ensure every value falls within an acceptable range.
     *
     * @throws IllegalArgumentException when any field contains an invalid value
     */
    public void validate() {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("MaxRetries cannot be negative");
        }
        if (retryBackoff == null || retryBackoff.isNegative()) {
            throw new IllegalArgumentException("RetryBackoff cannot be negative");
        }
        if (rateLimit <= 0) {
            throw new IllegalArgumentException("RateLimit must be positive");
        }
        if (circuitBreakerThreshold <= 0) {
            throw new IllegalArgumentException("CircuitBreakerThreshold must be positive");
        }
        if (circuitBreakerWindow == null || circuitBreakerWindow.isNegative() || circuitBreakerWindow.isZero()) {
            throw new IllegalArgumentException("CircuitBreakerWindow must be positive");
        }
    }
    
    /**
     * Merge the current configuration with the system-wide maximums to enforce guardrails.
     *
     * @param max system-wide maxima used as upper bounds
     * @return a new configuration where each value respects the maximum constraints
     */
    public ResilienceConfig mergeWithMax(ResilienceConfig max) {
        return new ResilienceConfig(
            timeout.compareTo(max.timeout) > 0 ? max.timeout : timeout,
            Math.min(maxRetries, max.maxRetries),
            retryBackoff.compareTo(max.retryBackoff) > 0 ? max.retryBackoff : retryBackoff,
            Math.min(rateLimit, max.rateLimit),
            Math.min(circuitBreakerThreshold, max.circuitBreakerThreshold),
            circuitBreakerWindow.compareTo(max.circuitBreakerWindow) > 0 
                ? max.circuitBreakerWindow 
                : circuitBreakerWindow,
            responseHeaderWhitelist != null && !responseHeaderWhitelist.isEmpty()
                ? responseHeaderWhitelist 
                : max.responseHeaderWhitelist
        );
    }
}
