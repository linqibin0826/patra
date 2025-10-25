package com.patra.starter.provenance.common.converter;

import com.patra.starter.provenance.common.config.ProvenanceConfig;
import com.patra.starter.provenance.common.http.HttpResilienceConfig;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Converter between provenance-specific configuration and HTTP resilience configuration used by the
 * built-in simple HTTP client.
 *
 * <p>This converter is used in direct HTTP access scenarios (non-gateway mode) to extract retry,
 * timeout, and rate limit settings from provenance configuration.
 *
 * @author linqibin
 * @since 0.1.0
 */
public final class ProvenanceConfigConverter {

  private ProvenanceConfigConverter() {
    // Utility class
  }

  /**
   * Converts ProvenanceConfig to lightweight HTTP resilience configuration.
   *
   * @param config provenance configuration
   * @return resilience configuration for the simple HTTP client, never null
   */
  public static HttpResilienceConfig toHttpResilienceConfig(ProvenanceConfig config) {
    if (config == null) {
      return new HttpResilienceConfig(null, null, null, null);
    }

    Long timeoutSeconds = extractTimeoutSeconds(config);
    Integer maxRetries = extractMaxRetries(config);
    Long retryBackoffSeconds = extractRetryBackoffSeconds(config);
    Integer rateLimit = extractRateLimit(config);

    return new HttpResilienceConfig(timeoutSeconds, maxRetries, retryBackoffSeconds, rateLimit);
  }

  /**
   * Extracts default headers from ProvenanceConfig.
   *
   * @param config provenance configuration
   * @return immutable map of default headers, empty if none configured, never null
   */
  public static Map<String, String> extractHeaders(ProvenanceConfig config) {
    if (config == null || config.http() == null || config.http().defaultHeaders() == null) {
      return Map.of();
    }
    return Map.copyOf(config.http().defaultHeaders());
  }

  private static Long extractTimeoutSeconds(ProvenanceConfig config) {
    if (config.http() == null || config.http().timeoutTotalMillis() == null) {
      return null;
    }

    int millis = config.http().timeoutTotalMillis();
    if (millis <= 0) {
      return null;
    }

    long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);
    // Ensure at least 1 second for tiny timeouts
    return Math.max(1L, seconds == 0 ? 1L : seconds);
  }

  private static Integer extractMaxRetries(ProvenanceConfig config) {
    if (config.retry() == null || config.retry().maxRetryTimes() == null) {
      return null;
    }

    int maxRetries = config.retry().maxRetryTimes();
    return maxRetries > 0 ? maxRetries : null;
  }

  private static Long extractRetryBackoffSeconds(ProvenanceConfig config) {
    if (config.retry() == null || config.retry().initialDelayMillis() == null) {
      return null;
    }

    long millis = config.retry().initialDelayMillis();
    if (millis <= 0) {
      return 0L;
    }

    long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);
    // Ensure at least 1 second for tiny backoffs
    return Math.max(1L, seconds == 0 ? 1L : seconds);
  }

  private static Integer extractRateLimit(ProvenanceConfig config) {
    if (config.rateLimit() == null || config.rateLimit().perCredentialQpsLimit() == null) {
      return null;
    }

    int configuredLimit = config.rateLimit().perCredentialQpsLimit();
    return configuredLimit > 0 ? configuredLimit : null;
  }
}
