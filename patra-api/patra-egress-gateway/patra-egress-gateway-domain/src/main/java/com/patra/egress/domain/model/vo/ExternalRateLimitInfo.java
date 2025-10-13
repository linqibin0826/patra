package com.patra.egress.domain.model.vo;

import java.util.List;
import java.util.Map;

/**
 * Value object representing rate limit details returned by an external provider.
 *
 * @param limit maximum allowed requests (X-RateLimit-Limit)
 * @param remaining remaining quota (X-RateLimit-Remaining)
 * @param resetTimestamp epoch second when the quota resets (X-RateLimit-Reset)
 * @author linqibin
 * @since 0.1.0
 */
public record ExternalRateLimitInfo(Integer limit, Integer remaining, Long resetTimestamp) {
  /**
   * Derive rate limit information from HTTP response headers. Header names are treated
   * case-insensitively.
   *
   * @param headers response headers returned by the provider
   * @return populated {@link ExternalRateLimitInfo} or {@code null} when no limit information is
   *     present
   */
  public static ExternalRateLimitInfo fromHeaders(Map<String, List<String>> headers) {
    if (headers == null || headers.isEmpty()) {
      return null;
    }

    Integer limit = extractIntHeader(headers, "X-RateLimit-Limit");
    Integer remaining = extractIntHeader(headers, "X-RateLimit-Remaining");
    Long resetTimestamp = extractLongHeader(headers, "X-RateLimit-Reset");

    // If the provider does not supply any rate limit hints, return null to signal absence.
    if (limit == null && remaining == null && resetTimestamp == null) {
      return null;
    }

    return new ExternalRateLimitInfo(limit, remaining, resetTimestamp);
  }

  /**
   * Extract an integer header value using a case-insensitive lookup.
   *
   * @param headers response headers
   * @param headerName header key to retrieve
   * @return parsed integer value or {@code null} when unavailable or invalid
   */
  private static Integer extractIntHeader(Map<String, List<String>> headers, String headerName) {
    List<String> values = getHeaderIgnoreCase(headers, headerName);
    if (values == null || values.isEmpty()) {
      return null;
    }

    try {
      return Integer.parseInt(values.get(0));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /**
   * Extract a long header value using a case-insensitive lookup.
   *
   * @param headers response headers
   * @param headerName header key to retrieve
   * @return parsed long value or {@code null} when unavailable or invalid
   */
  private static Long extractLongHeader(Map<String, List<String>> headers, String headerName) {
    List<String> values = getHeaderIgnoreCase(headers, headerName);
    if (values == null || values.isEmpty()) {
      return null;
    }

    try {
      return Long.parseLong(values.get(0));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /**
   * Retrieve a header value while ignoring the case of the header name.
   *
   * @param headers response headers
   * @param headerName header key to retrieve
   * @return list of header values or {@code null} when the header is absent
   */
  private static List<String> getHeaderIgnoreCase(
      Map<String, List<String>> headers, String headerName) {
    // Prefer a direct lookup for performance.
    List<String> values = headers.get(headerName);
    if (values != null) {
      return values;
    }

    // Fallback to a case-insensitive scan when the header key differs in case.
    for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
      if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(headerName)) {
        return entry.getValue();
      }
    }

    return null;
  }
}
