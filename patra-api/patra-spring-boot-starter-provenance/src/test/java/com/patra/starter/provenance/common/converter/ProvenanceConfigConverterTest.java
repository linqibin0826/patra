package com.patra.starter.provenance.common.converter;

import static org.assertj.core.api.Assertions.*;

import com.patra.starter.provenance.common.config.*;
import com.patra.starter.provenance.common.http.HttpResilienceConfig;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ProvenanceConfigConverter 单元测试
 *
 * @author linqibin
 */
@DisplayName("ProvenanceConfigConverter 测试")
class ProvenanceConfigConverterTest {

  @Test
  @DisplayName("toHttpResilienceConfig - null配置返回默认值")
  void toHttpResilienceConfig_shouldReturnDefaults_whenConfigIsNull() {
    // Act
    HttpResilienceConfig result = ProvenanceConfigConverter.toHttpResilienceConfig(null);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.timeoutSeconds()).isNull();
    assertThat(result.maxRetries()).isNull();
    assertThat(result.retryBackoffSeconds()).isNull();
    assertThat(result.rateLimitQps()).isNull();
  }

  @Test
  @DisplayName("toHttpResilienceConfig - 完整配置转换")
  void toHttpResilienceConfig_shouldConvertCompleteConfig() {
    // Arrange
    ProvenanceConfig config = createTestConfig(5000, 3, 1000L, 10);

    // Act
    HttpResilienceConfig result = ProvenanceConfigConverter.toHttpResilienceConfig(config);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.timeoutSeconds()).isEqualTo(5L); // 5000ms -> 5s
    assertThat(result.maxRetries()).isEqualTo(3);
    assertThat(result.retryBackoffSeconds()).isEqualTo(1L); // 1000ms -> 1s
    assertThat(result.rateLimitQps()).isEqualTo(10);
  }

  @Test
  @DisplayName("toHttpResilienceConfig - 超时毫秒正确转换为秒")
  void toHttpResilienceConfig_shouldConvertMillisToSeconds_forTimeout() {
    // Arrange
    ProvenanceConfig config = createTestConfig(3500, null, null, null);

    // Act
    HttpResilienceConfig result = ProvenanceConfigConverter.toHttpResilienceConfig(config);

    // Assert
    assertThat(result.timeoutSeconds()).isEqualTo(3L); // 向下取整
  }

  @Test
  @DisplayName("toHttpResilienceConfig - 小于1秒的超时设置为1秒")
  void toHttpResilienceConfig_shouldSetMinimumOneSecond_forTinyTimeout() {
    // Arrange
    ProvenanceConfig config = createTestConfig(500, null, null, null);

    // Act
    HttpResilienceConfig result = ProvenanceConfigConverter.toHttpResilienceConfig(config);

    // Assert
    assertThat(result.timeoutSeconds()).isEqualTo(1L); // 最小1秒
  }

  @Test
  @DisplayName("toHttpResilienceConfig - 负数或零超时忽略")
  void toHttpResilienceConfig_shouldIgnoreInvalidTimeout() {
    // Arrange
    ProvenanceConfig config = createTestConfig(-1000, null, null, null);

    // Act
    HttpResilienceConfig result = ProvenanceConfigConverter.toHttpResilienceConfig(config);

    // Assert
    assertThat(result.timeoutSeconds()).isNull();
  }

  @Test
  @DisplayName("toHttpResilienceConfig - 负数或零重试次数忽略")
  void toHttpResilienceConfig_shouldIgnoreInvalidRetries() {
    // Arrange
    ProvenanceConfig config = createTestConfig(null, -1, null, null);

    // Act
    HttpResilienceConfig result = ProvenanceConfigConverter.toHttpResilienceConfig(config);

    // Assert
    assertThat(result.maxRetries()).isNull();
  }

  @Test
  @DisplayName("extractHeaders - 提取默认请求头")
  void extractHeaders_shouldExtractDefaultHeaders() {
    // Arrange
    Map<String, String> headers =
        Map.of(
            "User-Agent", "Patra/1.0",
            "Accept", "application/json");

    HttpConfig httpConfig = new HttpConfig(headers, null, null, 5000);
    ProvenanceConfig config =
        new ProvenanceConfig("https://api.example.com", httpConfig, null, null, null, null, null);

    // Act
    Map<String, String> result = ProvenanceConfigConverter.extractHeaders(config);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result).hasSize(2);
    assertThat(result).containsEntry("User-Agent", "Patra/1.0");
    assertThat(result).containsEntry("Accept", "application/json");
  }

  @Test
  @DisplayName("extractHeaders - null配置返回空Map")
  void extractHeaders_shouldReturnEmptyMap_whenConfigIsNull() {
    // Act
    Map<String, String> result = ProvenanceConfigConverter.extractHeaders(null);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("extractHeaders - 无HttpConfig返回空Map")
  void extractHeaders_shouldReturnEmptyMap_whenHttpConfigIsNull() {
    // Arrange
    ProvenanceConfig config =
        new ProvenanceConfig("https://api.example.com", null, null, null, null, null, null);

    // Act
    Map<String, String> result = ProvenanceConfigConverter.extractHeaders(config);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("extractHeaders - 返回不可变Map")
  void extractHeaders_shouldReturnImmutableMap() {
    // Arrange
    HttpConfig httpConfig = new HttpConfig(Map.of("User-Agent", "Test/1.0"), null, null, 5000);
    ProvenanceConfig config =
        new ProvenanceConfig("https://api.example.com", httpConfig, null, null, null, null, null);

    // Act
    Map<String, String> result = ProvenanceConfigConverter.extractHeaders(config);

    // Assert
    assertThatThrownBy(() -> result.put("New-Header", "value"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  // Helper method to create test config
  private ProvenanceConfig createTestConfig(
      Integer timeoutMillis, Integer maxRetries, Long backoffMillis, Integer qpsLimit) {
    HttpConfig httpConfig =
        timeoutMillis != null ? new HttpConfig(null, null, null, timeoutMillis) : null;

    RetryConfig retryConfig =
        (maxRetries != null || backoffMillis != null)
            ? new RetryConfig(maxRetries, backoffMillis != null ? backoffMillis.intValue() : null)
            : null;

    RateLimitConfig rateLimitConfig = qpsLimit != null ? new RateLimitConfig(null, qpsLimit) : null;

    return new ProvenanceConfig(
        "https://api.example.com",
        httpConfig,
        null, // pagination
        null, // windowOffset
        null, // batching
        retryConfig,
        rateLimitConfig);
  }
}
