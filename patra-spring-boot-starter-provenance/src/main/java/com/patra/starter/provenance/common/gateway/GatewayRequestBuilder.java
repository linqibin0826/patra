package com.patra.starter.provenance.common.gateway;

import com.patra.egress.api.dto.ExternalCallRequestDTO;
import com.patra.egress.api.dto.ResilienceConfigDTO;
import com.patra.starter.provenance.common.config.ProvenanceConfig;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Gateway request builder.
 *
 * <p>Transforms provenance API requests into {@link ExternalCallRequestDTO} objects understood by
 * the egress gateway. The builder normalises URLs, encodes query parameters, propagates default
 * headers and translates provenance resilience hints to gateway overrides.
 *
 * <p>Automatically switches to POST method when query parameters exceed URL length limits (e.g.,
 * when fetching > 200 PubMed IDs), following NCBI E-utilities best practices.
 *
 * @author linqibin
 * @since 0.1.0
 */
public class GatewayRequestBuilder {

  /**
   * Maximum safe URL length before switching to POST.
   *
   * <p>Most servers support 2048 chars, but we use 1800 as a safety margin to account for base URL
   * and other parameters. This typically allows ~200 PubMed IDs (8 digits each + commas).
   */
  private static final int MAX_URL_LENGTH = 1800;

  /**
   * Build gateway request, automatically selecting GET or POST based on parameter size.
   *
   * <p>Uses GET for short parameter lists and POST for large ID lists (following PubMed
   * recommendation: use POST when > 200 UIDs).
   *
   * @param baseUrl provenance base URL (already normalised)
   * @param path API path starting with '/'
   * @param request request payload carrying query parameters
   * @param config provenance configuration overrides
   * @return external call request with appropriate HTTP method
   */
  public ExternalCallRequestDTO build(
      String baseUrl, String path, ApiRequest request, ProvenanceConfig config) {
    Assert.hasText(baseUrl, "baseUrl must not be blank");
    Assert.hasText(path, "path must not be blank");
    Assert.notNull(request, "request must not be null");
    Assert.notNull(config, "config must not be null");

    String normalizedPath = path.startsWith("/") ? path : "/" + path;
    String fullUrl = baseUrl + normalizedPath;

    Map<String, String> queryParams = request.toQueryParams();
    Map<String, String> headers = new LinkedHashMap<>();
    if (config.http() != null && !config.http().defaultHeaders().isEmpty()) {
      headers.putAll(config.http().defaultHeaders());
    }

    // Determine HTTP method: use POST if query string would exceed URL length limit
    if (queryParams != null && !queryParams.isEmpty()) {
      String queryString = buildQueryString(queryParams);
      if (StringUtils.hasText(queryString)) {
        int estimatedUrlLength = fullUrl.length() + queryString.length() + 1; // +1 for '?'

        if (estimatedUrlLength > MAX_URL_LENGTH) {
          // Use POST with form-encoded body
          headers.put("Content-Type", "application/x-www-form-urlencoded");
          return new ExternalCallRequestDTO(
              fullUrl,
              "POST",
              headers.isEmpty() ? null : Map.copyOf(headers),
              queryString,
              convertToResilienceConfig(config));
        } else {
          // Use GET with query string in URL
          fullUrl = fullUrl + "?" + queryString;
        }
      }
    }

    return new ExternalCallRequestDTO(
        fullUrl,
        "GET",
        headers.isEmpty() ? null : Map.copyOf(headers),
        null,
        convertToResilienceConfig(config));
  }

  private String buildQueryString(Map<String, String> params) {
    return params.entrySet().stream()
        .filter(entry -> StringUtils.hasText(entry.getKey()) && entry.getValue() != null)
        .map(
            entry ->
                entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
        .collect(Collectors.joining("&"));
  }

  private ResilienceConfigDTO convertToResilienceConfig(ProvenanceConfig config) {
    Long timeoutSeconds = null;
    if (config.http() != null && config.http().timeoutTotalMillis() != null) {
      int millis = config.http().timeoutTotalMillis();
      if (millis > 0) {
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);
        timeoutSeconds = Math.max(1L, seconds == 0 ? 1L : seconds);
      }
    }

    Integer maxRetries = null;
    Long retryBackoffSeconds = null;
    if (config.retry() != null) {
      maxRetries = config.retry().maxRetryTimes();
      if (config.retry().initialDelayMillis() != null) {
        long millis = config.retry().initialDelayMillis();
        retryBackoffSeconds =
            millis > 0 ? Math.max(1L, TimeUnit.MILLISECONDS.toSeconds(millis)) : 0L;
      }
    }

    Integer rateLimit = null;
    if (config.rateLimit() != null && config.rateLimit().perCredentialQpsLimit() != null) {
      int configuredLimit = config.rateLimit().perCredentialQpsLimit();
      if (configuredLimit > 0) {
        rateLimit = configuredLimit;
      }
    }

    return new ResilienceConfigDTO(
        timeoutSeconds, maxRetries, retryBackoffSeconds, rateLimit, null, null, null);
  }
}
