package com.patra.egress.infra.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for SensitiveHeaderMasker
 *
 * @author linqibin
 * @since 0.1.0
 */
@DisplayName("SensitiveHeaderMasker unit tests")
class SensitiveHeaderMaskerTest {

  @Test
  @DisplayName("Should mask the Authorization header")
  void shouldMaskAuthorizationHeader() {
    // Given
    Map<String, String> headers =
        Map.of(
            "Authorization", "Bearer secret-token",
            "Content-Type", "application/json");

    // When
    Map<String, String> masked = SensitiveHeaderMasker.mask(headers);

    // Then
    assertThat(masked.get("Authorization")).isEqualTo("***");
    assertThat(masked.get("Content-Type")).isEqualTo("application/json");
  }

  @Test
  @DisplayName("Should mask API-Key headers regardless of case")
  void shouldMaskApiKeyHeaderCaseInsensitive() {
    // Given
    Map<String, String> headers =
        Map.of(
            "api-key", "secret-key",
            "API-KEY", "another-secret",
            "Api-Key", "yet-another-secret");

    // When
    Map<String, String> masked = SensitiveHeaderMasker.mask(headers);

    // Then
    assertThat(masked.get("api-key")).isEqualTo("***");
    assertThat(masked.get("API-KEY")).isEqualTo("***");
    assertThat(masked.get("Api-Key")).isEqualTo("***");
  }

  @Test
  @DisplayName("Should mask Cookie and Set-Cookie headers")
  void shouldMaskCookieHeaders() {
    // Given
    Map<String, String> headers =
        Map.of(
            "Cookie", "session=abc123",
            "Set-Cookie", "session=xyz789; HttpOnly");

    // When
    Map<String, String> masked = SensitiveHeaderMasker.mask(headers);

    // Then
    assertThat(masked.get("Cookie")).isEqualTo("***");
    assertThat(masked.get("Set-Cookie")).isEqualTo("***");
  }

  @Test
  @DisplayName("Should mask all known sensitive headers")
  void shouldMaskAllKnownSensitiveHeaders() {
    // Given
    Map<String, String> headers =
        Map.of(
            "Authorization", "Bearer token",
            "x-api-key", "api-key",
            "Proxy-Authorization", "proxy-auth",
            "X-Auth-Token", "auth-token",
            "X-CSRF-Token", "csrf-token");

    // When
    Map<String, String> masked = SensitiveHeaderMasker.mask(headers);

    // Then
    assertThat(masked.get("Authorization")).isEqualTo("***");
    assertThat(masked.get("x-api-key")).isEqualTo("***");
    assertThat(masked.get("Proxy-Authorization")).isEqualTo("***");
    assertThat(masked.get("X-Auth-Token")).isEqualTo("***");
    assertThat(masked.get("X-CSRF-Token")).isEqualTo("***");
  }

  @Test
  @DisplayName("Should retain original values for non-sensitive headers")
  void shouldPreserveNonSensitiveHeaders() {
    // Given
    Map<String, String> headers =
        Map.of(
            "Content-Type", "application/json",
            "User-Agent", "Test-Client/1.0",
            "Accept", "application/json",
            "X-Request-ID", "req-123");

    // When
    Map<String, String> masked = SensitiveHeaderMasker.mask(headers);

    // Then
    assertThat(masked.get("Content-Type")).isEqualTo("application/json");
    assertThat(masked.get("User-Agent")).isEqualTo("Test-Client/1.0");
    assertThat(masked.get("Accept")).isEqualTo("application/json");
    assertThat(masked.get("X-Request-ID")).isEqualTo("req-123");
  }

  @Test
  @DisplayName("Should handle null and empty maps")
  void shouldHandleNullAndEmptyMap() {
    // When & Then: null map
    Map<String, String> maskedNull = SensitiveHeaderMasker.mask(null);
    assertThat(maskedNull).isEmpty();

    // When & Then: empty map
    Map<String, String> maskedEmpty = SensitiveHeaderMasker.mask(Map.of());
    assertThat(maskedEmpty).isEmpty();
  }

  @Test
  @DisplayName("Should handle mixes of sensitive and non-sensitive headers")
  void shouldHandleMixedHeaders() {
    // Given
    Map<String, String> headers =
        Map.of(
            "Authorization", "Bearer secret",
            "Content-Type", "application/json",
            "X-API-Key", "api-secret",
            "User-Agent", "Test-Client");

    // When
    Map<String, String> masked = SensitiveHeaderMasker.mask(headers);

    // Then
    assertThat(masked).hasSize(4);
    assertThat(masked.get("Authorization")).isEqualTo("***");
    assertThat(masked.get("Content-Type")).isEqualTo("application/json");
    assertThat(masked.get("X-API-Key")).isEqualTo("***");
    assertThat(masked.get("User-Agent")).isEqualTo("Test-Client");
  }
}
