package com.patra.egress.domain.model.aggregate;

import com.patra.egress.domain.model.vo.ResilienceConfig;
import com.patra.egress.domain.port.ConfigPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ResilienceConfigAggregate unit tests
 *
 * @author linqibin
 * @since 0.1.0
 */
@DisplayName("ResilienceConfigAggregate 聚合根测试")
class ResilienceConfigAggregateTest {

    private static final ResilienceConfig DEFAULT_CONFIG = new ResilienceConfig(
        Duration.ofSeconds(30),
        3,
        Duration.ofSeconds(2),
        100,
        10,
        Duration.ofSeconds(30),
        List.of("Content-Type", "Content-Length")
    );

    private static final ResilienceConfig MAX_CONFIG = new ResilienceConfig(
        Duration.ofSeconds(60),
        5,
        Duration.ofSeconds(10),
        1000,
        20,
        Duration.ofSeconds(60),
        List.of("Content-Type", "Content-Length", "X-RateLimit-Limit")
    );

    /**
     * 简单的 ConfigPort Mock 实现
     */
    private static class MockConfigPort implements ConfigPort {
        private final ResilienceConfig defaultConfig;
        private final ResilienceConfig maxConfig;

        MockConfigPort(ResilienceConfig defaultConfig, ResilienceConfig maxConfig) {
            this.defaultConfig = defaultConfig;
            this.maxConfig = maxConfig;
        }

        @Override
        public ResilienceConfig loadSystemDefaultConfig() {
            return defaultConfig;
        }

        @Override
        public ResilienceConfig loadSystemMaxConfig() {
            return maxConfig;
        }
    }

    @Test
    @DisplayName("loadSystemConfig() - 应该成功加载系统配置")
    void loadSystemConfig_shouldLoadSuccessfully() {
        // Given
        ConfigPort configPort = new MockConfigPort(DEFAULT_CONFIG, MAX_CONFIG);

        // When
        ResilienceConfigAggregate aggregate = ResilienceConfigAggregate.loadSystemConfig(configPort);

        // Then
        assertThat(aggregate).isNotNull();
        assertThat(aggregate.getSystemDefaultConfig()).isEqualTo(DEFAULT_CONFIG);
        assertThat(aggregate.getSystemMaxConfig()).isEqualTo(MAX_CONFIG);
    }

    @Test
    @DisplayName("loadSystemConfig() - 应该在默认配置无效时抛出异常")
    void loadSystemConfig_shouldThrowException_whenDefaultConfigInvalid() {
        // Given - 超时时间为负数的无效配置
        ResilienceConfig invalidConfig = new ResilienceConfig(
            Duration.ofSeconds(-1),
            3,
            Duration.ofSeconds(2),
            100,
            10,
            Duration.ofSeconds(30),
            List.of("Content-Type")
        );
        ConfigPort configPort = new MockConfigPort(invalidConfig, MAX_CONFIG);

        // When & Then
        assertThatThrownBy(() -> ResilienceConfigAggregate.loadSystemConfig(configPort))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Timeout must be positive");
    }

    @Test
    @DisplayName("loadSystemConfig() - 应该在最大配置无效时抛出异常")
    void loadSystemConfig_shouldThrowException_whenMaxConfigInvalid() {
        // Given - 限流速率为0的无效配置
        ResilienceConfig invalidMaxConfig = new ResilienceConfig(
            Duration.ofSeconds(60),
            5,
            Duration.ofSeconds(10),
            0, // 无效：限流速率必须为正数
            20,
            Duration.ofSeconds(60),
            List.of("Content-Type")
        );
        ConfigPort configPort = new MockConfigPort(DEFAULT_CONFIG, invalidMaxConfig);

        // When & Then
        assertThatThrownBy(() -> ResilienceConfigAggregate.loadSystemConfig(configPort))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("RateLimit must be positive");
    }

    @Test
    @DisplayName("mergeWithCallerConfig() - 应该在调用方未传递配置时返回系统默认配置")
    void mergeWithCallerConfig_shouldReturnDefaultConfig_whenCallerConfigIsNull() {
        // Given
        ConfigPort configPort = new MockConfigPort(DEFAULT_CONFIG, MAX_CONFIG);
        ResilienceConfigAggregate aggregate = ResilienceConfigAggregate.loadSystemConfig(configPort);

        // When
        ResilienceConfig merged = aggregate.mergeWithCallerConfig(null);

        // Then
        assertThat(merged).isEqualTo(DEFAULT_CONFIG);
    }

    @Test
    @DisplayName("mergeWithCallerConfig() - 应该使用调用方配置，如果未超过最大值")
    void mergeWithCallerConfig_shouldUseCallerConfig_whenNotExceedingMax() {
        // Given
        ConfigPort configPort = new MockConfigPort(DEFAULT_CONFIG, MAX_CONFIG);
        ResilienceConfigAggregate aggregate = ResilienceConfigAggregate.loadSystemConfig(configPort);

        ResilienceConfig callerConfig = new ResilienceConfig(
            Duration.ofSeconds(10), // 小于最大值60
            2,                      // 小于最大值5
            Duration.ofSeconds(1),  // 小于最大值10
            50,                     // 小于最大值1000
            5,                      // 小于最大值20
            Duration.ofSeconds(15), // 小于最大值60
            List.of("Custom-Header")
        );

        // When
        ResilienceConfig merged = aggregate.mergeWithCallerConfig(callerConfig);

        // Then
        assertThat(merged.timeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(merged.maxRetries()).isEqualTo(2);
        assertThat(merged.retryBackoff()).isEqualTo(Duration.ofSeconds(1));
        assertThat(merged.rateLimit()).isEqualTo(50);
        assertThat(merged.circuitBreakerThreshold()).isEqualTo(5);
        assertThat(merged.circuitBreakerWindow()).isEqualTo(Duration.ofSeconds(15));
        assertThat(merged.responseHeaderWhitelist()).containsExactly("Custom-Header");
    }

    @Test
    @DisplayName("mergeWithCallerConfig() - 应该限制调用方配置不超过最大值")
    void mergeWithCallerConfig_shouldLimitCallerConfig_whenExceedingMax() {
        // Given
        ConfigPort configPort = new MockConfigPort(DEFAULT_CONFIG, MAX_CONFIG);
        ResilienceConfigAggregate aggregate = ResilienceConfigAggregate.loadSystemConfig(configPort);

        ResilienceConfig callerConfig = new ResilienceConfig(
            Duration.ofSeconds(90),  // 超过最大值60
            10,                      // 超过最大值5
            Duration.ofSeconds(20),  // 超过最大值10
            2000,                    // 超过最大值1000
            30,                      // 超过最大值20
            Duration.ofSeconds(120), // 超过最大值60
            List.of("Custom-Header")
        );

        // When
        ResilienceConfig merged = aggregate.mergeWithCallerConfig(callerConfig);

        // Then - 应该使用最大值
        assertThat(merged.timeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(merged.maxRetries()).isEqualTo(5);
        assertThat(merged.retryBackoff()).isEqualTo(Duration.ofSeconds(10));
        assertThat(merged.rateLimit()).isEqualTo(1000);
        assertThat(merged.circuitBreakerThreshold()).isEqualTo(20);
        assertThat(merged.circuitBreakerWindow()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("mergeWithCallerConfig() - 应该在调用方配置无效时抛出异常")
    void mergeWithCallerConfig_shouldThrowException_whenCallerConfigInvalid() {
        // Given
        ConfigPort configPort = new MockConfigPort(DEFAULT_CONFIG, MAX_CONFIG);
        ResilienceConfigAggregate aggregate = ResilienceConfigAggregate.loadSystemConfig(configPort);

        ResilienceConfig invalidCallerConfig = new ResilienceConfig(
            Duration.ofSeconds(30),
            -1, // 无效：重试次数不能为负数
            Duration.ofSeconds(2),
            100,
            10,
            Duration.ofSeconds(30),
            List.of("Content-Type")
        );

        // When & Then
        assertThatThrownBy(() -> aggregate.mergeWithCallerConfig(invalidCallerConfig))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("MaxRetries cannot be negative");
    }

    @Test
    @DisplayName("mergeWithCallerConfig() - 应该使用调用方的白名单，如果提供了")
    void mergeWithCallerConfig_shouldUseCallerWhitelist_whenProvided() {
        // Given
        ConfigPort configPort = new MockConfigPort(DEFAULT_CONFIG, MAX_CONFIG);
        ResilienceConfigAggregate aggregate = ResilienceConfigAggregate.loadSystemConfig(configPort);

        ResilienceConfig callerConfig = new ResilienceConfig(
            Duration.ofSeconds(30),
            3,
            Duration.ofSeconds(2),
            100,
            10,
            Duration.ofSeconds(30),
            List.of("X-Custom-1", "X-Custom-2")
        );

        // When
        ResilienceConfig merged = aggregate.mergeWithCallerConfig(callerConfig);

        // Then
        assertThat(merged.responseHeaderWhitelist())
            .containsExactly("X-Custom-1", "X-Custom-2");
    }

    @Test
    @DisplayName("mergeWithCallerConfig() - 应该使用系统最大白名单，如果调用方未提供")
    void mergeWithCallerConfig_shouldUseMaxWhitelist_whenCallerWhitelistEmpty() {
        // Given
        ConfigPort configPort = new MockConfigPort(DEFAULT_CONFIG, MAX_CONFIG);
        ResilienceConfigAggregate aggregate = ResilienceConfigAggregate.loadSystemConfig(configPort);

        ResilienceConfig callerConfig = new ResilienceConfig(
            Duration.ofSeconds(30),
            3,
            Duration.ofSeconds(2),
            100,
            10,
            Duration.ofSeconds(30),
            List.of() // 空白名单
        );

        // When
        ResilienceConfig merged = aggregate.mergeWithCallerConfig(callerConfig);

        // Then - 应该使用最大配置的白名单
        assertThat(merged.responseHeaderWhitelist())
            .containsExactly("Content-Type", "Content-Length", "X-RateLimit-Limit");
    }

    @Test
    @DisplayName("validate() - 应该在配置有效时不抛出异常")
    void validate_shouldNotThrowException_whenConfigValid() {
        // Given
        ConfigPort configPort = new MockConfigPort(DEFAULT_CONFIG, MAX_CONFIG);
        ResilienceConfigAggregate aggregate = ResilienceConfigAggregate.loadSystemConfig(configPort);

        // When & Then - 不应该抛出任何异常
        aggregate.validate();
    }
}
