package com.patra.egress.domain.model.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RetryAdvice unit tests
 *
 * @author linqibin
 * @since 0.1.0
 */
@DisplayName("RetryAdvice 值对象测试")
class RetryAdviceTest {

    private static final ResilienceConfig DEFAULT_CONFIG = new ResilienceConfig(
        Duration.ofSeconds(30),
        3,
        Duration.ofSeconds(2),
        100,
        10,
        Duration.ofSeconds(30),
        List.of("Content-Type")
    );

    @Test
    @DisplayName("fromResponse() - 应该对429状态码建议重试")
    void fromResponse_shouldSuggestRetry_when429TooManyRequests() {
        // Given
        HttpResponse response = new HttpResponse(
            429,
            Map.of(),
            "Rate limit exceeded"
        );

        // When
        RetryAdvice advice = RetryAdvice.fromResponse(response, DEFAULT_CONFIG);

        // Then
        assertThat(advice.retryable()).isTrue();
        assertThat(advice.suggestedDelay()).isEqualTo(Duration.ofSeconds(2));
        assertThat(advice.reason()).isEqualTo("Rate limited");
    }

    @Test
    @DisplayName("fromResponse() - 应该对503状态码建议重试")
    void fromResponse_shouldSuggestRetry_when503ServiceUnavailable() {
        // Given
        HttpResponse response = new HttpResponse(
            503,
            Map.of(),
            "Service unavailable"
        );

        // When
        RetryAdvice advice = RetryAdvice.fromResponse(response, DEFAULT_CONFIG);

        // Then
        assertThat(advice.retryable()).isTrue();
        assertThat(advice.suggestedDelay()).isEqualTo(Duration.ofSeconds(2));
        assertThat(advice.reason()).isEqualTo("Service unavailable");
    }

    @Test
    @DisplayName("fromResponse() - 应该对408状态码建议重试")
    void fromResponse_shouldSuggestRetry_when408RequestTimeout() {
        // Given
        HttpResponse response = new HttpResponse(
            408,
            Map.of(),
            "Request timeout"
        );

        // When
        RetryAdvice advice = RetryAdvice.fromResponse(response, DEFAULT_CONFIG);

        // Then
        assertThat(advice.retryable()).isTrue();
        assertThat(advice.suggestedDelay()).isEqualTo(Duration.ofSeconds(2));
        assertThat(advice.reason()).isEqualTo("Request timeout");
    }

    @Test
    @DisplayName("fromResponse() - 应该对5xx状态码建议重试")
    void fromResponse_shouldSuggestRetry_when5xxServerError() {
        // Given
        HttpResponse response500 = new HttpResponse(500, Map.of(), "Internal server error");
        HttpResponse response502 = new HttpResponse(502, Map.of(), "Bad gateway");
        HttpResponse response504 = new HttpResponse(504, Map.of(), "Gateway timeout");

        // When
        RetryAdvice advice500 = RetryAdvice.fromResponse(response500, DEFAULT_CONFIG);
        RetryAdvice advice502 = RetryAdvice.fromResponse(response502, DEFAULT_CONFIG);
        RetryAdvice advice504 = RetryAdvice.fromResponse(response504, DEFAULT_CONFIG);

        // Then
        assertThat(advice500.retryable()).isTrue();
        assertThat(advice500.reason()).isEqualTo("Server error");

        assertThat(advice502.retryable()).isTrue();
        assertThat(advice502.reason()).isEqualTo("Server error");

        assertThat(advice504.retryable()).isTrue();
        assertThat(advice504.reason()).isEqualTo("Server error");
    }

    @Test
    @DisplayName("fromResponse() - 应该对2xx状态码不建议重试")
    void fromResponse_shouldNotSuggestRetry_when2xxSuccess() {
        // Given
        HttpResponse response200 = new HttpResponse(200, Map.of(), "OK");
        HttpResponse response201 = new HttpResponse(201, Map.of(), "Created");

        // When
        RetryAdvice advice200 = RetryAdvice.fromResponse(response200, DEFAULT_CONFIG);
        RetryAdvice advice201 = RetryAdvice.fromResponse(response201, DEFAULT_CONFIG);

        // Then
        assertThat(advice200.retryable()).isFalse();
        assertThat(advice200.suggestedDelay()).isEqualTo(Duration.ZERO);
        assertThat(advice200.reason()).isEqualTo("Not retryable");

        assertThat(advice201.retryable()).isFalse();
        assertThat(advice201.suggestedDelay()).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("fromResponse() - 应该对4xx状态码（除408/429）不建议重试")
    void fromResponse_shouldNotSuggestRetry_when4xxClientError() {
        // Given
        HttpResponse response400 = new HttpResponse(400, Map.of(), "Bad request");
        HttpResponse response401 = new HttpResponse(401, Map.of(), "Unauthorized");
        HttpResponse response404 = new HttpResponse(404, Map.of(), "Not found");

        // When
        RetryAdvice advice400 = RetryAdvice.fromResponse(response400, DEFAULT_CONFIG);
        RetryAdvice advice401 = RetryAdvice.fromResponse(response401, DEFAULT_CONFIG);
        RetryAdvice advice404 = RetryAdvice.fromResponse(response404, DEFAULT_CONFIG);

        // Then
        assertThat(advice400.retryable()).isFalse();
        assertThat(advice401.retryable()).isFalse();
        assertThat(advice404.retryable()).isFalse();
    }

    @Test
    @DisplayName("fromResponse() - 应该使用Retry-After响应头作为建议延迟")
    void fromResponse_shouldUseRetryAfterHeader_whenPresent() {
        // Given
        HttpResponse response = new HttpResponse(
            429,
            Map.of("Retry-After", List.of("10")), // 10秒后重试
            "Rate limit exceeded"
        );

        // When
        RetryAdvice advice = RetryAdvice.fromResponse(response, DEFAULT_CONFIG);

        // Then
        assertThat(advice.retryable()).isTrue();
        assertThat(advice.suggestedDelay()).isEqualTo(Duration.ofSeconds(10));
        assertThat(advice.reason()).isEqualTo("Rate limited");
    }

    @Test
    @DisplayName("fromResponse() - 应该在Retry-After头不是数字时使用默认退避时间")
    void fromResponse_shouldUseDefaultBackoff_whenRetryAfterIsNotNumeric() {
        // Given
        HttpResponse response = new HttpResponse(
            429,
            Map.of("Retry-After", List.of("not-a-number")),
            "Rate limit exceeded"
        );

        // When
        RetryAdvice advice = RetryAdvice.fromResponse(response, DEFAULT_CONFIG);

        // Then
        assertThat(advice.retryable()).isTrue();
        assertThat(advice.suggestedDelay()).isEqualTo(Duration.ofSeconds(2)); // 使用配置的退避时间
    }

    @Test
    @DisplayName("fromResponse() - 应该忽略大小写匹配Retry-After响应头")
    void fromResponse_shouldMatchRetryAfterHeaderCaseInsensitive() {
        // Given
        HttpResponse response = new HttpResponse(
            429,
            Map.of("retry-after", List.of("5")), // 小写
            "Rate limit exceeded"
        );

        // When
        RetryAdvice advice = RetryAdvice.fromResponse(response, DEFAULT_CONFIG);

        // Then
        assertThat(advice.retryable()).isTrue();
        assertThat(advice.suggestedDelay()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("fromResponse() - 应该在503响应中使用Retry-After头")
    void fromResponse_shouldUseRetryAfterHeader_when503WithRetryAfter() {
        // Given
        HttpResponse response = new HttpResponse(
            503,
            Map.of("Retry-After", List.of("30")),
            "Service unavailable"
        );

        // When
        RetryAdvice advice = RetryAdvice.fromResponse(response, DEFAULT_CONFIG);

        // Then
        assertThat(advice.retryable()).isTrue();
        assertThat(advice.suggestedDelay()).isEqualTo(Duration.ofSeconds(30));
        assertThat(advice.reason()).isEqualTo("Service unavailable");
    }
}
