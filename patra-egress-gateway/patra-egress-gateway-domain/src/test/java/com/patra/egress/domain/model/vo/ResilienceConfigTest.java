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
@DisplayName("ResilienceConfig value object tests")
class ResilienceConfigTest {

    @Test
    @DisplayName("validate() should throw when the timeout is negative")
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
    @DisplayName("validate() should throw when the timeout is zero")
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
    @DisplayName("validate() should throw when max retries is negative")
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
    @DisplayName("validate() should throw when retry backoff is negative")
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
    @DisplayName("validate() should throw when rate limit is non-positive")
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
    @DisplayName("validate() should throw when the circuit breaker threshold is non-positive")
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
    @DisplayName("validate() should throw when the circuit breaker window is zero or negative")
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
    @DisplayName("validate() should pass when every value is valid")
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

        // When & Then - no exception should be raised
        config.validate();
    }

    @Test
    @DisplayName("mergeWithMax() should cap the timeout at the maximum")
    void mergeWithMax_shouldLimitTimeout_whenExceedingMax() {
        // Given
        ResilienceConfig caller = new ResilienceConfig(
            Duration.ofSeconds(90), // Above the maximum value
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
    @DisplayName("mergeWithMax() should cap the retry count at the maximum")
    void mergeWithMax_shouldLimitMaxRetries_whenExceedingMax() {
        // Given
        ResilienceConfig caller = new ResilienceConfig(
            Duration.ofSeconds(30),
            10, // Above the maximum value
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
    @DisplayName("mergeWithMax() should cap the rate limit at the maximum")
    void mergeWithMax_shouldLimitRateLimit_whenExceedingMax() {
        // Given
        ResilienceConfig caller = new ResilienceConfig(
            Duration.ofSeconds(30),
            3,
            Duration.ofSeconds(2),
            2000, // Above the maximum value
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
    @DisplayName("mergeWithMax() should honour caller settings when they are within bounds")
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
    @DisplayName("mergeWithMax() should use the caller whitelist when provided")
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
    @DisplayName("mergeWithMax() should fall back to the system whitelist when the caller omits it")
    void mergeWithMax_shouldUseMaxWhitelist_whenCallerWhitelistIsEmpty() {
        // Given
        ResilienceConfig caller = new ResilienceConfig(
            Duration.ofSeconds(30),
            3,
            Duration.ofSeconds(2),
            100,
            10,
            Duration.ofSeconds(30),
            List.of() // Caller did not provide a whitelist
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
    @DisplayName("Constructor should create an immutable copy of the whitelist")
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

        // Mutate the original list
        whitelist.add("Header-3");

        // Then - the whitelist inside the configuration should remain unchanged
        assertThat(config.responseHeaderWhitelist()).hasSize(2);
        assertThat(config.responseHeaderWhitelist()).containsExactly("Header-1", "Header-2");
    }
}
