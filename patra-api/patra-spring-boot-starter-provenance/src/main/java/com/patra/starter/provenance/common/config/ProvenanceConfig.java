package com.patra.starter.provenance.common.config;

import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * Provenance data source configuration.
 *
 * <p>Field descriptions:
 *
 * @param baseUrl canonical base URL of the upstream data source
 * @param http HTTP client settings including timeouts and headers
 * @param pagination default pagination hints for callers
 * @param windowOffset sliding window defaults used during incremental harvest
 * @param batching request batching parameters for bulk fetch operations
 * @param retry retry strategy overrides forwarded to the gateway
 * @param rateLimit rate limiting hints for credential-aware throttling
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
    RateLimitConfig rateLimit) {
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
