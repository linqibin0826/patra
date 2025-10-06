package com.patra.egress.domain.model.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HttpResponse unit tests
 *
 * @author linqibin
 * @since 0.1.0
 */
@DisplayName("HttpResponse 值对象测试")
class HttpResponseTest {

    @Test
    @DisplayName("isSuccess() - 应该对200状态码返回true")
    void isSuccess_shouldReturnTrue_when200OK() {
        // Given
        HttpResponse response = new HttpResponse(200, Map.of(), "OK");

        // When & Then
        assertThat(response.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("isSuccess() - 应该对所有2xx状态码返回true")
    void isSuccess_shouldReturnTrue_whenStatus2xx() {
        // Given & When & Then
        assertThat(new HttpResponse(200, Map.of(), "OK").isSuccess()).isTrue();
        assertThat(new HttpResponse(201, Map.of(), "Created").isSuccess()).isTrue();
        assertThat(new HttpResponse(202, Map.of(), "Accepted").isSuccess()).isTrue();
        assertThat(new HttpResponse(204, Map.of(), "No Content").isSuccess()).isTrue();
        assertThat(new HttpResponse(299, Map.of(), "Custom").isSuccess()).isTrue();
    }

    @Test
    @DisplayName("isSuccess() - 应该对4xx状态码返回false")
    void isSuccess_shouldReturnFalse_whenStatus4xx() {
        // Given & When & Then
        assertThat(new HttpResponse(400, Map.of(), "Bad Request").isSuccess()).isFalse();
        assertThat(new HttpResponse(401, Map.of(), "Unauthorized").isSuccess()).isFalse();
        assertThat(new HttpResponse(404, Map.of(), "Not Found").isSuccess()).isFalse();
        assertThat(new HttpResponse(429, Map.of(), "Too Many Requests").isSuccess()).isFalse();
    }

    @Test
    @DisplayName("isSuccess() - 应该对5xx状态码返回false")
    void isSuccess_shouldReturnFalse_whenStatus5xx() {
        // Given & When & Then
        assertThat(new HttpResponse(500, Map.of(), "Internal Server Error").isSuccess()).isFalse();
        assertThat(new HttpResponse(502, Map.of(), "Bad Gateway").isSuccess()).isFalse();
        assertThat(new HttpResponse(503, Map.of(), "Service Unavailable").isSuccess()).isFalse();
        assertThat(new HttpResponse(504, Map.of(), "Gateway Timeout").isSuccess()).isFalse();
    }

    @Test
    @DisplayName("isSuccess() - 应该对3xx状态码返回false")
    void isSuccess_shouldReturnFalse_whenStatus3xx() {
        // Given & When & Then
        assertThat(new HttpResponse(301, Map.of(), "Moved Permanently").isSuccess()).isFalse();
        assertThat(new HttpResponse(302, Map.of(), "Found").isSuccess()).isFalse();
        assertThat(new HttpResponse(304, Map.of(), "Not Modified").isSuccess()).isFalse();
    }

    @Test
    @DisplayName("构造函数 - 应该创建不可变的响应头副本")
    void constructor_shouldCreateImmutableCopyOfHeaders() {
        // Given
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Content-Type", List.of("application/json"));
        headers.put("Content-Length", List.of("1234"));

        // When
        HttpResponse response = new HttpResponse(200, headers, "OK");

        // 修改原始映射
        headers.put("X-Custom-Header", List.of("custom-value"));

        // Then - 响应中的响应头应该不受影响
        assertThat(response.headers()).hasSize(2);
        assertThat(response.headers()).containsOnlyKeys("Content-Type", "Content-Length");
    }
}
