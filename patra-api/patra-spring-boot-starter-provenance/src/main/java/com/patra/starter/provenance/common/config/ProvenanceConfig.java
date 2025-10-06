package com.patra.starter.provenance.common.config;

/**
 * Provenance data source configuration
 *
 * @author linqibin
 * @since 0.1.0
 */
public record ProvenanceConfig(
    String baseUrl,
    HttpConfig http,
    PaginationConfig pagination,
    WindowOffsetConfig windowOffset,
    BatchingConfig batching,
    RetryConfig retry,
    RateLimitConfig rateLimit
) {
}
