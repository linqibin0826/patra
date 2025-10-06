package com.patra.starter.provenance.common.config;

/**
 * Rate limit configuration.
 *
 * <p>Field descriptions:
 * @param maxConcurrentRequests concurrency guard for simultaneously active operations
 * @param perCredentialQpsLimit desired QPS limit per credential set
 *
 * @author linqibin
 * @since 0.1.0
 */
public record RateLimitConfig(
    Integer maxConcurrentRequests,
    Integer perCredentialQpsLimit
) {
}
