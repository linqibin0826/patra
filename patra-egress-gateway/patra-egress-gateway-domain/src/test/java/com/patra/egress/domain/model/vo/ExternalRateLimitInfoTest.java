package com.patra.egress.domain.model.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ExternalRateLimitInfo unit tests
 *
 * @author linqibin
 * @since 0.1.0
 */
@DisplayName("ExternalRateLimitInfo 值对象测试")
class ExternalRateLimitInfoTest {

    @Test
    @DisplayName("fromHeaders() - 应该正确提取标准格式的限流响应头")
    void fromHeaders_shouldExtractRateLimitHeaders_withStandardFormat() {
        // Given
        Map<String, List<String>> headers = Map.of(
            "X-RateLimit-Limit", List.of("100"),
            "X-RateLimit-Remaining", List.of("75"),
            "X-RateLimit-Reset", List.of("1672531200")
        );

        // When
        ExternalRateLimitInfo info = ExternalRateLimitInfo.fromHeaders(headers);

        // Then
        assertThat(info).isNotNull();
        assertThat(info.limit()).isEqualTo(100);
        assertThat(info.remaining()).isEqualTo(75);
        assertThat(info.resetTimestamp()).isEqualTo(1672531200L);
    }

    @Test
    @DisplayName("fromHeaders() - 应该忽略大小写提取响应头（小写）")
    void fromHeaders_shouldExtractRateLimitHeaders_withLowercaseHeaders() {
        // Given
        Map<String, List<String>> headers = Map.of(
            "x-ratelimit-limit", List.of("100"),
            "x-ratelimit-remaining", List.of("75"),
            "x-ratelimit-reset", List.of("1672531200")
        );

        // When
        ExternalRateLimitInfo info = ExternalRateLimitInfo.fromHeaders(headers);

        // Then
        assertThat(info).isNotNull();
        assertThat(info.limit()).isEqualTo(100);
        assertThat(info.remaining()).isEqualTo(75);
        assertThat(info.resetTimestamp()).isEqualTo(1672531200L);
    }

    @Test
    @DisplayName("fromHeaders() - 应该忽略大小写提取响应头（混合大小写）")
    void fromHeaders_shouldExtractRateLimitHeaders_withMixedCaseHeaders() {
        // Given
        Map<String, List<String>> headers = Map.of(
            "X-RateLimit-LIMIT", List.of("100"),
            "x-RateLimit-Remaining", List.of("75"),
            "X-rateLimit-Reset", List.of("1672531200")
        );

        // When
        ExternalRateLimitInfo info = ExternalRateLimitInfo.fromHeaders(headers);

        // Then
        assertThat(info).isNotNull();
        assertThat(info.limit()).isEqualTo(100);
        assertThat(info.remaining()).isEqualTo(75);
        assertThat(info.resetTimestamp()).isEqualTo(1672531200L);
    }

    @Test
    @DisplayName("fromHeaders() - 应该在只有部分限流头时返回部分信息")
    void fromHeaders_shouldReturnPartialInfo_whenOnlySomeHeadersPresent() {
        // Given - 只有 limit 和 remaining
        Map<String, List<String>> headers = Map.of(
            "X-RateLimit-Limit", List.of("100"),
            "X-RateLimit-Remaining", List.of("75")
        );

        // When
        ExternalRateLimitInfo info = ExternalRateLimitInfo.fromHeaders(headers);

        // Then
        assertThat(info).isNotNull();
        assertThat(info.limit()).isEqualTo(100);
        assertThat(info.remaining()).isEqualTo(75);
        assertThat(info.resetTimestamp()).isNull();
    }

    @Test
    @DisplayName("fromHeaders() - 应该在没有限流头时返回null")
    void fromHeaders_shouldReturnNull_whenNoRateLimitHeaders() {
        // Given
        Map<String, List<String>> headers = Map.of(
            "Content-Type", List.of("application/json"),
            "Content-Length", List.of("1234")
        );

        // When
        ExternalRateLimitInfo info = ExternalRateLimitInfo.fromHeaders(headers);

        // Then
        assertThat(info).isNull();
    }

    @Test
    @DisplayName("fromHeaders() - 应该在响应头为空时返回null")
    void fromHeaders_shouldReturnNull_whenHeadersEmpty() {
        // Given
        Map<String, List<String>> headers = Map.of();

        // When
        ExternalRateLimitInfo info = ExternalRateLimitInfo.fromHeaders(headers);

        // Then
        assertThat(info).isNull();
    }

    @Test
    @DisplayName("fromHeaders() - 应该在响应头为null时返回null")
    void fromHeaders_shouldReturnNull_whenHeadersNull() {
        // When
        ExternalRateLimitInfo info = ExternalRateLimitInfo.fromHeaders(null);

        // Then
        assertThat(info).isNull();
    }

    @Test
    @DisplayName("fromHeaders() - 应该在响应头值为非数字时忽略该字段")
    void fromHeaders_shouldIgnoreInvalidValue_whenHeaderValueIsNotNumeric() {
        // Given
        Map<String, List<String>> headers = Map.of(
            "X-RateLimit-Limit", List.of("not-a-number"),
            "X-RateLimit-Remaining", List.of("75"),
            "X-RateLimit-Reset", List.of("1672531200")
        );

        // When
        ExternalRateLimitInfo info = ExternalRateLimitInfo.fromHeaders(headers);

        // Then
        assertThat(info).isNotNull();
        assertThat(info.limit()).isNull(); // 解析失败，应该为null
        assertThat(info.remaining()).isEqualTo(75);
        assertThat(info.resetTimestamp()).isEqualTo(1672531200L);
    }

    @Test
    @DisplayName("fromHeaders() - 应该在响应头值列表为空时返回null")
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
    @DisplayName("fromHeaders() - 应该取第一个值，如果响应头有多个值")
    void fromHeaders_shouldUseFirstValue_whenHeaderHasMultipleValues() {
        // Given
        Map<String, List<String>> headers = Map.of(
            "X-RateLimit-Limit", List.of("100", "200"), // 多个值，应该取第一个
            "X-RateLimit-Remaining", List.of("75"),
            "X-RateLimit-Reset", List.of("1672531200")
        );

        // When
        ExternalRateLimitInfo info = ExternalRateLimitInfo.fromHeaders(headers);

        // Then
        assertThat(info).isNotNull();
        assertThat(info.limit()).isEqualTo(100); // 应该是第一个值
        assertThat(info.remaining()).isEqualTo(75);
        assertThat(info.resetTimestamp()).isEqualTo(1672531200L);
    }
}
