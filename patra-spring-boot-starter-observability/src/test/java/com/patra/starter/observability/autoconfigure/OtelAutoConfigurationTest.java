package com.patra.starter.observability.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.starter.observability.config.ObservabilityProperties;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;

///
/// OtelAutoConfiguration 单元测试。
///
/// 验证 OpenTelemetry 自动配置正确创建并注册所有必要的 Bean。
/// 遵循 TDD 流程：先定义期望行为，再实现配置类。
///
/// @author Jobs
/// @since 1.0.0
///
class OtelAutoConfigurationTest {

  /// 默认启用 OTLP 的测试场景。
  @Nested
  @DisplayName("OTLP 启用时")
  @SpringBootTest(classes = OtelAutoConfigurationTest.TestConfiguration.class)
  @TestPropertySource(
      properties = {
        "patra.observability.enabled=true",
        "patra.observability.exporter.enabled=true",
        "patra.observability.exporter.endpoint=http://localhost:4317",
        "management.observations.annotations.enabled=false"
      })
  class OtlpEnabledTests {

    @Autowired private ApplicationContext context;

    @Autowired(required = false)
    private OpenTelemetry openTelemetry;

    @Autowired(required = false)
    private Tracer tracer;

    @Autowired(required = false)
    private ObservabilityProperties properties;

    /// 测试 OpenTelemetry SDK Bean 创建。
    @Test
    @DisplayName("应该创建 OpenTelemetry Bean")
    void shouldCreateOpenTelemetryBean() {
      assertThat(openTelemetry).isNotNull();
      assertThat(openTelemetry).isInstanceOf(OpenTelemetrySdk.class);
    }

    /// 测试 Tracer Bean 创建。
    @Test
    @DisplayName("应该创建 Tracer Bean")
    void shouldCreateTracerBean() {
      assertThat(tracer).isNotNull();
    }

    /// 测试 OTLP 配置属性绑定。
    @Test
    @DisplayName("应该正确绑定 OTLP 配置属性")
    void shouldBindOtlpConfigurationProperties() {
      assertThat(properties).isNotNull();
      assertThat(properties.getExporter().isEnabled()).isTrue();
      assertThat(properties.getExporter().getEndpoint()).isEqualTo("http://localhost:4317");
    }

    /// 测试 SdkTracerProvider Bean 创建。
    @Test
    @DisplayName("应该创建 SdkTracerProvider Bean")
    void shouldCreateSdkTracerProviderBean() {
      String[] beanNames = context.getBeanNamesForType(SdkTracerProvider.class);
      assertThat(beanNames).isNotEmpty();
    }
  }

  /// OTLP 禁用时的测试场景。
  @Nested
  @DisplayName("OTLP 禁用时")
  @SpringBootTest(classes = OtelAutoConfigurationTest.TestConfiguration.class)
  @TestPropertySource(
      properties = {
        "patra.observability.enabled=true",
        "patra.observability.exporter.enabled=false",
        "management.observations.annotations.enabled=false"
      })
  class OtlpDisabledTests {

    @Autowired private ApplicationContext context;

    /// 测试 OTLP 禁用时不创建 OpenTelemetry Bean。
    @Test
    @DisplayName("不应该创建 OpenTelemetry Bean")
    void shouldNotCreateOpenTelemetryBeanWhenDisabled() {
      // OTel 相关 Bean 应该不存在（或是 noop 实现）
      String[] beanNames = context.getBeanNamesForType(SdkTracerProvider.class);
      assertThat(beanNames).isEmpty();
    }
  }

  /// 测试配置类。
  @Configuration
  @EnableAutoConfiguration(
      exclude = {
        DataSourceAutoConfiguration.class,
        RedisAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class,
        org.redisson.spring.starter.RedissonAutoConfigurationV2.class
      })
  static class TestConfiguration {
    // 使用自动配置，让 Spring Boot 加载 OtelAutoConfiguration
  }
}
