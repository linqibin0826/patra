package com.patra.egress.infra.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.egress.domain.model.vo.ResilienceConfig;
import com.patra.egress.infra.config.properties.EgressProperties;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration tests for YamlConfigRepository Tests configuration loading from YAML files
 *
 * @author linqibin
 * @since 0.1.0
 */
@SpringBootTest(classes = {YamlConfigRepository.class})
@EnableConfigurationProperties(EgressProperties.class)
@ActiveProfiles("test")
@DisplayName("YamlConfigRepository integration tests")
class YamlConfigRepositoryTest {

  @Autowired private YamlConfigRepository yamlConfigRepository;

  @Autowired private EgressProperties egressProperties;

  @Test
  @DisplayName("loadSystemDefaultConfig() should load the system defaults from YAML")
  void loadSystemDefaultConfig_shouldLoadFromYaml() {
    // When
    ResilienceConfig config = yamlConfigRepository.loadSystemDefaultConfig();

    // Then
    assertThat(config).isNotNull();
    assertThat(config.timeout()).isEqualTo(Duration.ofSeconds(30));
    assertThat(config.maxRetries()).isEqualTo(3);
    assertThat(config.retryBackoff()).isEqualTo(Duration.ofSeconds(2));
    assertThat(config.rateLimit()).isEqualTo(100);
    assertThat(config.circuitBreakerThreshold()).isEqualTo(50);
    assertThat(config.circuitBreakerWindow()).isEqualTo(Duration.ofSeconds(60));
    assertThat(config.responseHeaderWhitelist())
        .containsExactlyInAnyOrder(
            "Content-Type", "X-RateLimit-Limit", "X-RateLimit-Remaining", "Retry-After", "ETag");
  }

  @Test
  @DisplayName("loadSystemMaxConfig() should load the system maxima from YAML")
  void loadSystemMaxConfig_shouldLoadFromYaml() {
    // When
    ResilienceConfig config = yamlConfigRepository.loadSystemMaxConfig();

    // Then
    assertThat(config).isNotNull();
    assertThat(config.timeout()).isEqualTo(Duration.ofSeconds(60));
    assertThat(config.maxRetries()).isEqualTo(5);
    assertThat(config.retryBackoff()).isEqualTo(Duration.ofSeconds(10));
    assertThat(config.rateLimit()).isEqualTo(1000);
    assertThat(config.circuitBreakerThreshold()).isEqualTo(50);
    assertThat(config.circuitBreakerWindow()).isEqualTo(Duration.ofSeconds(30));
    assertThat(config.responseHeaderWhitelist())
        .containsExactlyInAnyOrder(
            "Content-Type",
            "X-RateLimit-Limit",
            "X-RateLimit-Remaining",
            "X-RateLimit-Reset",
            "Retry-After",
            "ETag",
            "Last-Modified");
  }

  @Test
  @DisplayName("loadSystemDefaultConfig() should return an immutable whitelist")
  void loadSystemDefaultConfig_shouldReturnImmutableWhitelist() {
    // When
    ResilienceConfig config = yamlConfigRepository.loadSystemDefaultConfig();

    // Then
    assertThat(config.responseHeaderWhitelist()).isUnmodifiable();
  }

  @Test
  @DisplayName("loadSystemMaxConfig() should return an immutable whitelist")
  void loadSystemMaxConfig_shouldReturnImmutableWhitelist() {
    // When
    ResilienceConfig config = yamlConfigRepository.loadSystemMaxConfig();

    // Then
    assertThat(config.responseHeaderWhitelist()).isUnmodifiable();
  }

  @Test
  @DisplayName("EgressProperties should load global configuration correctly")
  void egressProperties_shouldLoadGlobalConfig() {
    // Then
    assertThat(egressProperties).isNotNull();
    assertThat(egressProperties.getGlobal()).isNotNull();
    assertThat(egressProperties.getGlobal().getRateLimit()).isEqualTo(1000);
  }

  @Test
  @DisplayName("Configuration loading - default timeout should be less than the maximum")
  void configLoading_defaultTimeoutShouldBeLessThanMax() {
    // When
    ResilienceConfig defaultConfig = yamlConfigRepository.loadSystemDefaultConfig();
    ResilienceConfig maxConfig = yamlConfigRepository.loadSystemMaxConfig();

    // Then
    assertThat(defaultConfig.timeout()).isLessThan(maxConfig.timeout());
  }

  @Test
  @DisplayName(
      "Configuration loading - default retries should be less than or equal to the maximum")
  void configLoading_defaultRetriesShouldBeLessThanOrEqualToMax() {
    // When
    ResilienceConfig defaultConfig = yamlConfigRepository.loadSystemDefaultConfig();
    ResilienceConfig maxConfig = yamlConfigRepository.loadSystemMaxConfig();

    // Then
    assertThat(defaultConfig.maxRetries()).isLessThanOrEqualTo(maxConfig.maxRetries());
  }
}
