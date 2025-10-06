package com.patra.egress.infra.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SensitiveHeaderMasker
 *
 * @author linqibin
 * @since 0.1.0
 */
@DisplayName("SensitiveHeaderMasker 单元测试")
class SensitiveHeaderMaskerTest {

    @Test
    @DisplayName("应该脱敏 Authorization 头")
    void shouldMaskAuthorizationHeader() {
        // Given
        Map<String, String> headers = Map.of(
                "Authorization", "Bearer secret-token",
                "Content-Type", "application/json"
        );

        // When
        Map<String, String> masked = SensitiveHeaderMasker.mask(headers);

        // Then
        assertThat(masked.get("Authorization")).isEqualTo("***");
        assertThat(masked.get("Content-Type")).isEqualTo("application/json");
    }

    @Test
    @DisplayName("应该脱敏 API-Key 头（不区分大小写）")
    void shouldMaskApiKeyHeaderCaseInsensitive() {
        // Given
        Map<String, String> headers = Map.of(
                "api-key", "secret-key",
                "API-KEY", "another-secret",
                "Api-Key", "yet-another-secret"
        );

        // When
        Map<String, String> masked = SensitiveHeaderMasker.mask(headers);

        // Then
        assertThat(masked.get("api-key")).isEqualTo("***");
        assertThat(masked.get("API-KEY")).isEqualTo("***");
        assertThat(masked.get("Api-Key")).isEqualTo("***");
    }

    @Test
    @DisplayName("应该脱敏 Cookie 和 Set-Cookie 头")
    void shouldMaskCookieHeaders() {
        // Given
        Map<String, String> headers = Map.of(
                "Cookie", "session=abc123",
                "Set-Cookie", "session=xyz789; HttpOnly"
        );

        // When
        Map<String, String> masked = SensitiveHeaderMasker.mask(headers);

        // Then
        assertThat(masked.get("Cookie")).isEqualTo("***");
        assertThat(masked.get("Set-Cookie")).isEqualTo("***");
    }

    @Test
    @DisplayName("应该脱敏所有已知的敏感头")
    void shouldMaskAllKnownSensitiveHeaders() {
        // Given
        Map<String, String> headers = Map.of(
                "Authorization", "Bearer token",
                "x-api-key", "api-key",
                "Proxy-Authorization", "proxy-auth",
                "X-Auth-Token", "auth-token",
                "X-CSRF-Token", "csrf-token"
        );

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
    @DisplayName("应该保留非敏感头的原始值")
    void shouldPreserveNonSensitiveHeaders() {
        // Given
        Map<String, String> headers = Map.of(
                "Content-Type", "application/json",
                "User-Agent", "Test-Client/1.0",
                "Accept", "application/json",
                "X-Request-ID", "req-123"
        );

        // When
        Map<String, String> masked = SensitiveHeaderMasker.mask(headers);

        // Then
        assertThat(masked.get("Content-Type")).isEqualTo("application/json");
        assertThat(masked.get("User-Agent")).isEqualTo("Test-Client/1.0");
        assertThat(masked.get("Accept")).isEqualTo("application/json");
        assertThat(masked.get("X-Request-ID")).isEqualTo("req-123");
    }

    @Test
    @DisplayName("应该处理 null 和空 map")
    void shouldHandleNullAndEmptyMap() {
        // When & Then: null map
        Map<String, String> maskedNull = SensitiveHeaderMasker.mask(null);
        assertThat(maskedNull).isEmpty();

        // When & Then: empty map
        Map<String, String> maskedEmpty = SensitiveHeaderMasker.mask(Map.of());
        assertThat(maskedEmpty).isEmpty();
    }

    @Test
    @DisplayName("应该处理混合敏感和非敏感头")
    void shouldHandleMixedHeaders() {
        // Given
        Map<String, String> headers = Map.of(
                "Authorization", "Bearer secret",
                "Content-Type", "application/json",
                "X-API-Key", "api-secret",
                "User-Agent", "Test-Client"
        );

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
