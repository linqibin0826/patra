package com.patra.egress.domain.model.vo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ExternalRateLimitInfo unit tests
 *
 * @author linqibin
 * @since 0.1.0
 */
@DisplayName("ExternalRateLimitInfo value object tests")
class ExternalRateLimitInfoTest {

  @Test
  @DisplayName("fromHeaders() should extract standard rate limit headers")
  void fromHeaders_shouldExtractRateLimitHeaders_withStandardFormat() {
    // Given
    Map<String, List<String>> headers =
        Map.of(
            "X-RateLimit-Limit", List.of("100"),
            "X-RateLimit-Remaining", List.of("75"),
            "X-RateLimit-Reset", List.of("1672531200"));

    // When
    ExternalRateLimitInfo info = ExternalRateLimitInfo.fromHeaders(headers);

    // Then
    assertThat(info).isNotNull();
    assertThat(info.limit()).isEqualTo(100);
    assertThat(info.remaining()).isEqualTo(75);
    assertThat(info.resetTimestamp()).isEqualTo(1672531200L);
  }

  @Test
  @DisplayName("fromHeaders() should perform case-insensitive header lookup (lowercase)")
  void fromHeaders_shouldExtractRateLimitHeaders_withLowercaseHeaders() {
    // Given
    Map<String, List<String>> headers =
        Map.of(
            "x-ratelimit-limit", List.of("100"),
            "x-ratelimit-remaining", List.of("75"),
            "x-ratelimit-reset", List.of("1672531200"));

    // When
    ExternalRateLimitInfo info = ExternalRateLimitInfo.fromHeaders(headers);

    // Then
    assertThat(info).isNotNull();
    assertThat(info.limit()).isEqualTo(100);
    assertThat(info.remaining()).isEqualTo(75);
    assertThat(info.resetTimestamp()).isEqualTo(1672531200L);
  }

  @Test
  @DisplayName("fromHeaders() should perform case-insensitive header lookup (mixed case)")
  void fromHeaders_shouldExtractRateLimitHeaders_withMixedCaseHeaders() {
    // Given
    Map<String, List<String>> headers =
        Map.of(
            "X-RateLimit-LIMIT", List.of("100"),
            "x-RateLimit-Remaining", List.of("75"),
            "X-rateLimit-Reset", List.of("1672531200"));

    // When
    ExternalRateLimitInfo info = ExternalRateLimitInfo.fromHeaders(headers);

    // Then
    assertThat(info).isNotNull();
    assertThat(info.limit()).isEqualTo(100);
    assertThat(info.remaining()).isEqualTo(75);
    assertThat(info.resetTimestamp()).isEqualTo(1672531200L);
  }

  @Test
  @DisplayName("fromHeaders() should return partial data when only some headers are present")
  void fromHeaders_shouldReturnPartialInfo_whenOnlySomeHeadersPresent() {
    // Given - only the limit and remaining headers are present
    Map<String, List<String>> headers =
        Map.of(
            "X-RateLimit-Limit", List.of("100"),
            "X-RateLimit-Remaining", List.of("75"));

    // When
    ExternalRateLimitInfo info = ExternalRateLimitInfo.fromHeaders(headers);

    // Then
    assertThat(info).isNotNull();
    assertThat(info.limit()).isEqualTo(100);
    assertThat(info.remaining()).isEqualTo(75);
    assertThat(info.resetTimestamp()).isNull();
  }

  @Test
  @DisplayName("fromHeaders() should return null when no rate limit headers exist")
  void fromHeaders_shouldReturnNull_whenNoRateLimitHeaders() {
    // Given
    Map<String, List<String>> headers =
        Map.of(
            "Content-Type", List.of("application/json"),
            "Content-Length", List.of("1234"));

    // When
    ExternalRateLimitInfo info = ExternalRateLimitInfo.fromHeaders(headers);

    // Then
    assertThat(info).isNull();
  }

  @Test
  @DisplayName("fromHeaders() should return null when headers are empty")
  void fromHeaders_shouldReturnNull_whenHeadersEmpty() {
    // Given
    Map<String, List<String>> headers = Map.of();

    // When
    ExternalRateLimitInfo info = ExternalRateLimitInfo.fromHeaders(headers);

    // Then
    assertThat(info).isNull();
  }

  @Test
  @DisplayName("fromHeaders() should return null when headers are null")
  void fromHeaders_shouldReturnNull_whenHeadersNull() {
    // When
    ExternalRateLimitInfo info = ExternalRateLimitInfo.fromHeaders(null);

    // Then
    assertThat(info).isNull();
  }

  @Test
  @DisplayName("fromHeaders() should ignore fields with non-numeric values")
  void fromHeaders_shouldIgnoreInvalidValue_whenHeaderValueIsNotNumeric() {
    // Given
    Map<String, List<String>> headers =
        Map.of(
            "X-RateLimit-Limit", List.of("not-a-number"),
            "X-RateLimit-Remaining", List.of("75"),
            "X-RateLimit-Reset", List.of("1672531200"));

    // When
    ExternalRateLimitInfo info = ExternalRateLimitInfo.fromHeaders(headers);

    // Then
    assertThat(info).isNotNull();
    assertThat(info.limit()).isNull(); // Parsing failed, should be null
    assertThat(info.remaining()).isEqualTo(75);
    assertThat(info.resetTimestamp()).isEqualTo(1672531200L);
  }

  @Test
  @DisplayName("fromHeaders() should return null when header values are empty")
  void fromHeaders_shouldReturnNull_whenHeaderValueListIsEmpty() {
    // Given
    Map<String, List<String>> headers = new HashMap<>();
    headers.put("X-RateLimit-Limit", List.of());

    // When
    ExternalRateLimitInfo info = ExternalRateLimitInfo.fromHeaders(headers);

    // Then
    assertThat(info).isNull();
  }

  @Test
  @DisplayName("fromHeaders() should use the first value when multiple values exist")
  void fromHeaders_shouldUseFirstValue_whenHeaderHasMultipleValues() {
    // Given
    Map<String, List<String>> headers =
        Map.of(
            "X-RateLimit-Limit", List.of("100", "200"), // Multiple values, should use the first
            "X-RateLimit-Remaining", List.of("75"),
            "X-RateLimit-Reset", List.of("1672531200"));

    // When
    ExternalRateLimitInfo info = ExternalRateLimitInfo.fromHeaders(headers);

    // Then
    assertThat(info).isNotNull();
    assertThat(info.limit()).isEqualTo(100); // Should equal the first value
    assertThat(info.remaining()).isEqualTo(75);
    assertThat(info.resetTimestamp()).isEqualTo(1672531200L);
  }
}
