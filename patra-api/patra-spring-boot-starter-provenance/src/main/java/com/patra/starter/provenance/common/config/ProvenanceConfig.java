package com.patra.starter.provenance.common.config;

import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * Provenance data source configuration.
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
    public ProvenanceConfig {
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalArgumentException("baseUrl cannot be null or blank");
        }
        baseUrl = trimTrailingSlash(baseUrl.trim());
        http = http != null ? http : new HttpConfig(Map.of(), null, null, null);
    }

    private String trimTrailingSlash(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }
}
