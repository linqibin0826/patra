package com.patra.egress.domain.model.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ResilienceConfig unit tests
 *
 * @author linqibin
 * @since 0.1.0
 */
@DisplayName("ResilienceConfig 值对象测试")
class ResilienceConfigTest {

    @Test
    @DisplayName("validate() - 应该在超时时间为负值时抛出异常")
    void validate_shouldThrowException_whenTimeoutIsNegative() {
        // Given
        ResilienceConfig config = new ResilienceConfig(
            Duration.ofSeconds(-1),
            3,
            Duration.ofSeconds(2),
            100,
            10,
            Duration.ofSeconds(30),
            List.of("Content-Type")
        );

        // When & Then
        assertThatThrownBy(config::validate)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Timeout must be positive");
    }

    @Test
    @DisplayName("validate() - 应该在超时时间为零时抛出异常")
    void validate_shouldThrowException_whenTimeoutIsZero() {
        // Given
        ResilienceConfig config = new ResilienceConfig(
            Duration.ZERO,
            3,
            Duration.ofSeconds(2),
            100,
            10,
            Duration.ofSeconds(30),
            List.of("Content-Type")
        );

        // When & Then
        assertThatThrownBy(config::validate)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Timeout must be positive");
    }

    @Test
    @DisplayName("validate() - 应该在最大重试次数为负值时抛出异常")
    void validate_shouldThrowException_whenMaxRetriesIsNegative() {
        // Given
        ResilienceConfig config = new ResilienceConfig(
            Duration.ofSeconds(30),
            -1,
            Duration.ofSeconds(2),
            100,
            10,
            Duration.ofSeconds(30),
            List.of("Content-Type")
        );

        // When & Then
        assertThatThrownBy(config::validate)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("MaxRetries cannot be negative");
    }

    @Test
    @DisplayName("validate() - 应该在重试退避时间为负值时抛出异常")
    void validate_shouldThrowException_whenRetryBackoffIsNegative() {
        // Given
        ResilienceConfig config = new ResilienceConfig(
            Duration.ofSeconds(30),
            3,
            Duration.ofSeconds(-1),
            100,
            10,
            Duration.ofSeconds(30),
            List.of("Content-Type")
        );

        // When & Then
        assertThatThrownBy(config::validate)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("RetryBackoff cannot be negative");
    }

    @Test
    @DisplayName("validate() - 应该在限流速率小于等于0时抛出异常")
    void validate_shouldThrowException_whenRateLimitIsNotPositive() {
        // Given
        ResilienceConfig config = new ResilienceConfig(
            Duration.ofSeconds(30),
            3,
            Duration.ofSeconds(2),
            0,
            10,
            Duration.ofSeconds(30),
            List.of("Content-Type")
        );

        // When & Then
        assertThatThrownBy(config::validate)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("RateLimit must be positive");
    }

    @Test
    @DisplayName("validate() - 应该在熔断阈值小于等于0时抛出异常")
    void validate_shouldThrowException_whenCircuitBreakerThresholdIsNotPositive() {
        // Given
        ResilienceConfig config = new ResilienceConfig(
            Duration.ofSeconds(30),
            3,
            Duration.ofSeconds(2),
            100,
            0,
            Duration.ofSeconds(30),
            List.of("Content-Type")
        );

        // When & Then
        assertThatThrownBy(config::validate)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("CircuitBreakerThreshold must be positive");
    }

    @Test
    @DisplayName("validate() - 应该在熔断时间窗口为零或负值时抛出异常")
    void validate_shouldThrowException_whenCircuitBreakerWindowIsZeroOrNegative() {
        // Given
        ResilienceConfig config = new ResilienceConfig(
            Duration.ofSeconds(30),
            3,
            Duration.ofSeconds(2),
            100,
            10,
            Duration.ZERO,
            List.of("Content-Type")
        );

        // When & Then
        assertThatThrownBy(config::validate)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("CircuitBreakerWindow must be positive");
    }

    @Test
    @DisplayName("validate() - 应该在所有配置都有效时不抛出异常")
    void validate_shouldNotThrowException_whenAllConfigIsValid() {
        // Given
        ResilienceConfig config = new ResilienceConfig(
            Duration.ofSeconds(30),
            3,
            Duration.ofSeconds(2),
            100,
            10,
            Duration.ofSeconds(30),
            List.of("Content-Type")
        );

        // When & Then - 不应该抛出任何异常
        config.validate();
    }

    @Test
    @DisplayName("mergeWithMax() - 应该限制超时时间不超过最大值")
    void mergeWithMax_shouldLimitTimeout_whenExceedingMax() {
        // Given
        ResilienceConfig caller = new ResilienceConfig(
            Duration.ofSeconds(90), // 超过最大值
            3,
            Duration.ofSeconds(2),
            100,
            10,
            Duration.ofSeconds(30),
            List.of("Content-Type")
        );

        ResilienceConfig max = new ResilienceConfig(
            Duration.ofSeconds(60),
            5,
            Duration.ofSeconds(10),
            1000,
            20,
            Duration.ofSeconds(60),
            List.of("Content-Type", "X-RateLimit-Limit")
        );

        // When
        ResilienceConfig merged = caller.mergeWithMax(max);

        // Then
        assertThat(merged.timeout()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("mergeWithMax() - 应该限制重试次数不超过最大值")
    void mergeWithMax_shouldLimitMaxRetries_whenExceedingMax() {
        // Given
        ResilienceConfig caller = new ResilienceConfig(
            Duration.ofSeconds(30),
            10, // 超过最大值
            Duration.ofSeconds(2),
            100,
            10,
            Duration.ofSeconds(30),
            List.of("Content-Type")
        );

        ResilienceConfig max = new ResilienceConfig(
            Duration.ofSeconds(60),
            5,
            Duration.ofSeconds(10),
            1000,
            20,
            Duration.ofSeconds(60),
            List.of("Content-Type", "X-RateLimit-Limit")
        );

        // When
        ResilienceConfig merged = caller.mergeWithMax(max);

        // Then
        assertThat(merged.maxRetries()).isEqualTo(5);
    }

    @Test
    @DisplayName("mergeWithMax() - 应该限制限流速率不超过最大值")
    void mergeWithMax_shouldLimitRateLimit_whenExceedingMax() {
        // Given
        ResilienceConfig caller = new ResilienceConfig(
            Duration.ofSeconds(30),
            3,
            Duration.ofSeconds(2),
            2000, // 超过最大值
            10,
            Duration.ofSeconds(30),
            List.of("Content-Type")
        );

        ResilienceConfig max = new ResilienceConfig(
            Duration.ofSeconds(60),
            5,
            Duration.ofSeconds(10),
            1000,
            20,
            Duration.ofSeconds(60),
            List.of("Content-Type", "X-RateLimit-Limit")
        );

        // When
        ResilienceConfig merged = caller.mergeWithMax(max);

        // Then
        assertThat(merged.rateLimit()).isEqualTo(1000);
    }

    @Test
    @DisplayName("mergeWithMax() - 应该使用调用方配置，如果未超过最大值")
    void mergeWithMax_shouldUseCallerConfig_whenNotExceedingMax() {
        // Given
        ResilienceConfig caller = new ResilienceConfig(
            Duration.ofSeconds(10),
            2,
            Duration.ofSeconds(1),
            50,
            5,
            Duration.ofSeconds(15),
            List.of("Custom-Header")
        );

        ResilienceConfig max = new ResilienceConfig(
            Duration.ofSeconds(60),
            5,
            Duration.ofSeconds(10),
            1000,
            20,
            Duration.ofSeconds(60),
            List.of("Content-Type", "X-RateLimit-Limit")
        );

        // When
        ResilienceConfig merged = caller.mergeWithMax(max);

        // Then
        assertThat(merged.timeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(merged.maxRetries()).isEqualTo(2);
        assertThat(merged.retryBackoff()).isEqualTo(Duration.ofSeconds(1));
        assertThat(merged.rateLimit()).isEqualTo(50);
        assertThat(merged.circuitBreakerThreshold()).isEqualTo(5);
        assertThat(merged.circuitBreakerWindow()).isEqualTo(Duration.ofSeconds(15));
    }

    @Test
    @DisplayName("mergeWithMax() - 应该使用调用方的响应头白名单，如果提供了")
    void mergeWithMax_shouldUseCallerWhitelist_whenProvided() {
        // Given
        ResilienceConfig caller = new ResilienceConfig(
            Duration.ofSeconds(30),
            3,
            Duration.ofSeconds(2),
            100,
            10,
            Duration.ofSeconds(30),
            List.of("Custom-Header-1", "Custom-Header-2")
        );

        ResilienceConfig max = new ResilienceConfig(
            Duration.ofSeconds(60),
            5,
            Duration.ofSeconds(10),
            1000,
            20,
            Duration.ofSeconds(60),
            List.of("Content-Type", "X-RateLimit-Limit")
        );

        // When
        ResilienceConfig merged = caller.mergeWithMax(max);

        // Then
        assertThat(merged.responseHeaderWhitelist())
            .containsExactly("Custom-Header-1", "Custom-Header-2");
    }

    @Test
    @DisplayName("mergeWithMax() - 应该使用最大配置的响应头白名单，如果调用方未提供")
    void mergeWithMax_shouldUseMaxWhitelist_whenCallerWhitelistIsEmpty() {
        // Given
        ResilienceConfig caller = new ResilienceConfig(
            Duration.ofSeconds(30),
            3,
            Duration.ofSeconds(2),
            100,
            10,
            Duration.ofSeconds(30),
            List.of() // 空白名单
        );

        ResilienceConfig max = new ResilienceConfig(
            Duration.ofSeconds(60),
            5,
            Duration.ofSeconds(10),
            1000,
            20,
            Duration.ofSeconds(60),
            List.of("Content-Type", "X-RateLimit-Limit")
        );

        // When
        ResilienceConfig merged = caller.mergeWithMax(max);

        // Then
        assertThat(merged.responseHeaderWhitelist())
            .containsExactly("Content-Type", "X-RateLimit-Limit");
    }

    @Test
    @DisplayName("构造函数 - 应该创建不可变的响应头白名单副本")
    void constructor_shouldCreateImmutableCopyOfWhitelist() {
        // Given
        List<String> whitelist = new java.util.ArrayList<>();
        whitelist.add("Header-1");
        whitelist.add("Header-2");

        // When
        ResilienceConfig config = new ResilienceConfig(
            Duration.ofSeconds(30),
            3,
            Duration.ofSeconds(2),
            100,
            10,
            Duration.ofSeconds(30),
            whitelist
        );

        // 修改原始列表
        whitelist.add("Header-3");

        // Then - 配置中的白名单应该不受影响
        assertThat(config.responseHeaderWhitelist()).hasSize(2);
        assertThat(config.responseHeaderWhitelist()).containsExactly("Header-1", "Header-2");
    }
}
