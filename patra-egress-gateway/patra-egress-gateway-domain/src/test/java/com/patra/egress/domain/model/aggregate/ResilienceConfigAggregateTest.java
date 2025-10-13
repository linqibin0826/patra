package com.patra.egress.domain.model.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patra.egress.domain.model.vo.ResilienceConfig;
import com.patra.egress.domain.port.ConfigPort;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ResilienceConfigAggregate unit tests
 *
 * @author linqibin
 * @since 0.1.0
 */
@DisplayName("ResilienceConfigAggregate aggregate tests")
class ResilienceConfigAggregateTest {

  private static final ResilienceConfig DEFAULT_CONFIG =
      new ResilienceConfig(
          Duration.ofSeconds(30),
          3,
          Duration.ofSeconds(2),
          100,
          10,
          Duration.ofSeconds(30),
          List.of("Content-Type", "Content-Length"));

  private static final ResilienceConfig MAX_CONFIG =
      new ResilienceConfig(
          Duration.ofSeconds(60),
          5,
          Duration.ofSeconds(10),
          1000,
          20,
          Duration.ofSeconds(60),
          List.of("Content-Type", "Content-Length", "X-RateLimit-Limit"));

  /** Simple {@link ConfigPort} mock implementation used for test scenarios. */
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
  @DisplayName("loadSystemConfig() should load the system configuration")
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
  @DisplayName("loadSystemConfig() should throw when the default configuration is invalid")
  void loadSystemConfig_shouldThrowException_whenDefaultConfigInvalid() {
    // Given an invalid configuration with a negative timeout
    ResilienceConfig invalidConfig =
        new ResilienceConfig(
            Duration.ofSeconds(-1),
            3,
            Duration.ofSeconds(2),
            100,
            10,
            Duration.ofSeconds(30),
            List.of("Content-Type"));
    ConfigPort configPort = new MockConfigPort(invalidConfig, MAX_CONFIG);

    // When & Then
    assertThatThrownBy(() -> ResilienceConfigAggregate.loadSystemConfig(configPort))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Timeout must be positive");
  }

  @Test
  @DisplayName("loadSystemConfig() should throw when the max configuration is invalid")
  void loadSystemConfig_shouldThrowException_whenMaxConfigInvalid() {
    // Given an invalid maximum configuration with a zero rate limit
    ResilienceConfig invalidMaxConfig =
        new ResilienceConfig(
            Duration.ofSeconds(60),
            5,
            Duration.ofSeconds(10),
            0, // Invalid: rate limit must be positive
            20,
            Duration.ofSeconds(60),
            List.of("Content-Type"));
    ConfigPort configPort = new MockConfigPort(DEFAULT_CONFIG, invalidMaxConfig);

    // When & Then
    assertThatThrownBy(() -> ResilienceConfigAggregate.loadSystemConfig(configPort))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("RateLimit must be positive");
  }

  @Test
  @DisplayName(
      "mergeWithCallerConfig() should return the system default when the caller omits configuration")
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
  @DisplayName(
      "mergeWithCallerConfig() should honour caller configuration when it respects max constraints")
  void mergeWithCallerConfig_shouldUseCallerConfig_whenNotExceedingMax() {
    // Given
    ConfigPort configPort = new MockConfigPort(DEFAULT_CONFIG, MAX_CONFIG);
    ResilienceConfigAggregate aggregate = ResilienceConfigAggregate.loadSystemConfig(configPort);

    ResilienceConfig callerConfig =
        new ResilienceConfig(
            Duration.ofSeconds(10), // Below the 60-second ceiling
            2, // Below the maximum of 5
            Duration.ofSeconds(1), // Below the 10-second ceiling
            50, // Below the maximum rate of 1000
            5, // Below the maximum threshold of 20
            Duration.ofSeconds(15), // Below the 60-second circuit window
            List.of("Custom-Header"));

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
  @DisplayName("mergeWithCallerConfig() should cap caller configuration at the system maxima")
  void mergeWithCallerConfig_shouldLimitCallerConfig_whenExceedingMax() {
    // Given
    ConfigPort configPort = new MockConfigPort(DEFAULT_CONFIG, MAX_CONFIG);
    ResilienceConfigAggregate aggregate = ResilienceConfigAggregate.loadSystemConfig(configPort);

    ResilienceConfig callerConfig =
        new ResilienceConfig(
            Duration.ofSeconds(90), // Above the maximum of 60 seconds
            10, // Above the maximum of 5 retries
            Duration.ofSeconds(20), // Above the maximum of 10 seconds
            2000, // Above the maximum rate of 1000
            30, // Above the maximum threshold of 20
            Duration.ofSeconds(120), // Above the maximum window of 60 seconds
            List.of("Custom-Header"));

    // When
    ResilienceConfig merged = aggregate.mergeWithCallerConfig(callerConfig);

    // Then the result should respect the maximum constraints
    assertThat(merged.timeout()).isEqualTo(Duration.ofSeconds(60));
    assertThat(merged.maxRetries()).isEqualTo(5);
    assertThat(merged.retryBackoff()).isEqualTo(Duration.ofSeconds(10));
    assertThat(merged.rateLimit()).isEqualTo(1000);
    assertThat(merged.circuitBreakerThreshold()).isEqualTo(20);
    assertThat(merged.circuitBreakerWindow()).isEqualTo(Duration.ofSeconds(60));
  }

  @Test
  @DisplayName("mergeWithCallerConfig() should throw when the caller configuration is invalid")
  void mergeWithCallerConfig_shouldThrowException_whenCallerConfigInvalid() {
    // Given
    ConfigPort configPort = new MockConfigPort(DEFAULT_CONFIG, MAX_CONFIG);
    ResilienceConfigAggregate aggregate = ResilienceConfigAggregate.loadSystemConfig(configPort);

    ResilienceConfig invalidCallerConfig =
        new ResilienceConfig(
            Duration.ofSeconds(30),
            -1, // Invalid: retry count cannot be negative
            Duration.ofSeconds(2),
            100,
            10,
            Duration.ofSeconds(30),
            List.of("Content-Type"));

    // When & Then
    assertThatThrownBy(() -> aggregate.mergeWithCallerConfig(invalidCallerConfig))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("MaxRetries cannot be negative");
  }

  @Test
  @DisplayName("mergeWithCallerConfig() should use the caller whitelist when provided")
  void mergeWithCallerConfig_shouldUseCallerWhitelist_whenProvided() {
    // Given
    ConfigPort configPort = new MockConfigPort(DEFAULT_CONFIG, MAX_CONFIG);
    ResilienceConfigAggregate aggregate = ResilienceConfigAggregate.loadSystemConfig(configPort);

    ResilienceConfig callerConfig =
        new ResilienceConfig(
            Duration.ofSeconds(30),
            3,
            Duration.ofSeconds(2),
            100,
            10,
            Duration.ofSeconds(30),
            List.of("X-Custom-1", "X-Custom-2"));

    // When
    ResilienceConfig merged = aggregate.mergeWithCallerConfig(callerConfig);

    // Then
    assertThat(merged.responseHeaderWhitelist()).containsExactly("X-Custom-1", "X-Custom-2");
  }

  @Test
  @DisplayName(
      "mergeWithCallerConfig() should fall back to the system whitelist when the caller omits it")
  void mergeWithCallerConfig_shouldUseMaxWhitelist_whenCallerWhitelistEmpty() {
    // Given
    ConfigPort configPort = new MockConfigPort(DEFAULT_CONFIG, MAX_CONFIG);
    ResilienceConfigAggregate aggregate = ResilienceConfigAggregate.loadSystemConfig(configPort);

    ResilienceConfig callerConfig =
        new ResilienceConfig(
            Duration.ofSeconds(30),
            3,
            Duration.ofSeconds(2),
            100,
            10,
            Duration.ofSeconds(30),
            List.of() // Empty whitelist from caller
            );

    // When
    ResilienceConfig merged = aggregate.mergeWithCallerConfig(callerConfig);

    // Then the merged configuration should reuse the system-wide whitelist
    assertThat(merged.responseHeaderWhitelist())
        .containsExactly("Content-Type", "Content-Length", "X-RateLimit-Limit");
  }

  @Test
  @DisplayName("validate() should pass when the configuration is valid")
  void validate_shouldNotThrowException_whenConfigValid() {
    // Given
    ConfigPort configPort = new MockConfigPort(DEFAULT_CONFIG, MAX_CONFIG);
    ResilienceConfigAggregate aggregate = ResilienceConfigAggregate.loadSystemConfig(configPort);

    // When & Then - no exceptions should be thrown
    aggregate.validate();
  }
}
