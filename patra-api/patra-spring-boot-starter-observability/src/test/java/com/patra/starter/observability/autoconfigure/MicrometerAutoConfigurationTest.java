package com.patra.starter.observability.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;

/// MicrometerAutoConfiguration 单元测试。
///
/// 验证 Micrometer 自动配置的 fallback 逻辑：
///
/// - applicationName 优先级：显式配置 > spring.application.name
/// - environment 优先级：显式配置 > spring.profiles.active（"default" 映射为 "dev"）
///
/// 测试方式：通过创建 Meter 并验证其公共标签来间接测试 CommonTagsMeterFilter 的行为。
///
/// @author Jobs
/// @since 1.0.0
class MicrometerAutoConfigurationTest {

  /// 显式配置场景 - 验证显式配置优先于 Spring 属性。
  @Nested
  @DisplayName("显式配置场景")
  @SpringBootTest(classes = MicrometerAutoConfigurationTest.TestConfig.class)
  @TestPropertySource(
      properties = {
        "spring.application.name=spring-app",
        "spring.profiles.active=prod",
        "patra.observability.application-name=explicit-app",
        "patra.observability.environment=explicit-env",
        "management.observations.annotations.enabled=false"
      })
  class ExplicitConfigurationTest {

    @Autowired private MeterRegistry meterRegistry;

    @Test
    @DisplayName("applicationName 应使用显式配置值（而非 spring.application.name）")
    void shouldUseExplicitApplicationName() {
      // 创建一个测试 Counter 来验证公共标签
      Counter counter = meterRegistry.counter("patra.test.explicit.app");

      // 验证 application 标签使用显式配置的值
      Tag applicationTag =
          counter.getId().getTags().stream()
              .filter(tag -> "application".equals(tag.getKey()))
              .findFirst()
              .orElse(null);

      assertThat(applicationTag).isNotNull();
      assertThat(applicationTag.getValue()).isEqualTo("explicit-app");
    }

    @Test
    @DisplayName("environment 应使用显式配置值（而非 spring.profiles.active）")
    void shouldUseExplicitEnvironment() {
      Counter counter = meterRegistry.counter("patra.test.explicit.env");

      Tag environmentTag =
          counter.getId().getTags().stream()
              .filter(tag -> "environment".equals(tag.getKey()))
              .findFirst()
              .orElse(null);

      assertThat(environmentTag).isNotNull();
      assertThat(environmentTag.getValue()).isEqualTo("explicit-env");
    }
  }

  /// Fallback 场景 - 验证从 Spring 属性自动获取。
  @Nested
  @DisplayName("Fallback 到 spring.application.name 和 spring.profiles.active")
  @SpringBootTest(classes = MicrometerAutoConfigurationTest.TestConfig.class)
  @TestPropertySource(
      properties = {
        "spring.application.name=fallback-spring-app",
        "spring.profiles.active=staging",
        "management.observations.annotations.enabled=false"
        // 注意：不配置 patra.observability.application-name 和 environment
      })
  class FallbackToSpringPropertiesTest {

    @Autowired private MeterRegistry meterRegistry;

    @Test
    @DisplayName("applicationName 应 fallback 到 spring.application.name")
    void shouldFallbackToSpringApplicationName() {
      Counter counter = meterRegistry.counter("patra.test.fallback.app");

      Tag applicationTag =
          counter.getId().getTags().stream()
              .filter(tag -> "application".equals(tag.getKey()))
              .findFirst()
              .orElse(null);

      assertThat(applicationTag).isNotNull();
      assertThat(applicationTag.getValue()).isEqualTo("fallback-spring-app");
    }

    @Test
    @DisplayName("environment 应 fallback 到 spring.profiles.active")
    void shouldFallbackToSpringProfilesActive() {
      Counter counter = meterRegistry.counter("patra.test.fallback.env");

      Tag environmentTag =
          counter.getId().getTags().stream()
              .filter(tag -> "environment".equals(tag.getKey()))
              .findFirst()
              .orElse(null);

      assertThat(environmentTag).isNotNull();
      assertThat(environmentTag.getValue()).isEqualTo("staging");
    }
  }

  /// Default Profile 映射场景 - 验证 "default" profile 映射为 "dev"。
  @Nested
  @DisplayName("default profile 映射为 dev")
  @SpringBootTest(classes = MicrometerAutoConfigurationTest.TestConfig.class)
  @TestPropertySource(
      properties = {
        "spring.application.name=default-profile-app",
        // 注意：不配置 spring.profiles.active，使用默认值 "default"
        "management.observations.annotations.enabled=false"
      })
  class DefaultProfileMappingTest {

    @Autowired private MeterRegistry meterRegistry;

    @Test
    @DisplayName("当 spring.profiles.active 为 default 时，environment 应映射为 dev")
    void shouldMapDefaultProfileToDev() {
      Counter counter = meterRegistry.counter("patra.test.default.profile");

      Tag environmentTag =
          counter.getId().getTags().stream()
              .filter(tag -> "environment".equals(tag.getKey()))
              .findFirst()
              .orElse(null);

      assertThat(environmentTag).isNotNull();
      assertThat(environmentTag.getValue()).isEqualTo("dev");
    }
  }

  /// 测试配置类。
  @Configuration
  @EnableAutoConfiguration(
      exclude = {
        DataSourceAutoConfiguration.class,
        DataRedisAutoConfiguration.class,
        DataRedisRepositoriesAutoConfiguration.class
      })
  static class TestConfig {

    @org.springframework.context.annotation.Primary
    @Bean
    @ConditionalOnMissingBean
    public MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }
  }
}
