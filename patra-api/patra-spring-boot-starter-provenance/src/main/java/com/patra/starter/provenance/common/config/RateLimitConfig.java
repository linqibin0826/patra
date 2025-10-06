package com.patra.starter.provenance.common.config;

/**
 * Rate limit configuration
 *
 * @author linqibin
 * @since 0.1.0
 */
public record RateLimitConfig(
    Integer maxConcurrentRequests,
    Integer perCredentialQpsLimit
) {
}
