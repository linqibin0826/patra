package com.patra.starter.observability.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.starter.observability.config.ObservabilityProperties;
import com.patra.starter.observability.config.ObservabilityProperties.OtlpExporterConfig;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import java.time.Duration;
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
/// OTLP Exporter 集成测试。
///
/// 验证 OpenTelemetry OTLP 导出器的自动配置和属性绑定。
///
/// @author Jobs
/// @since 1.0.0
///
class OtlpExporterIT {

  ///
  /// 默认配置测试场景。
  ///
  /// 验证使用默认配置时，所有 OTLP 相关 Bean 正确创建。
  ///
  @Nested
  @DisplayName("默认配置")
  @SpringBootTest(classes = OtlpExporterIT.TestConfiguration.class)
  @TestPropertySource(
      properties = {
        "spring.application.name=otlp-test-app",
        "patra.observability.enabled=true",
        "patra.observability.exporter.enabled=true",
        "patra.observability.logging.enabled=true",
        "management.observations.annotations.enabled=false"
      })
  class DefaultConfigTests {

    @Autowired private ApplicationContext context;

    @Autowired private ObservabilityProperties properties;

    @Autowired private OpenTelemetry openTelemetry;

    @Autowired private Resource otelResource;

    @Autowired private SdkTracerProvider sdkTracerProvider;

    @Autowired private SdkLoggerProvider sdkLoggerProvider;

    @Autowired private OtlpGrpcSpanExporter otlpGrpcSpanExporter;

    @Autowired private OtlpGrpcLogRecordExporter otlpGrpcLogRecordExporter;

    /// 测试默认 OTLP 端点配置。
    @Test
    @DisplayName("应该使用默认 OTLP 端点")
    void shouldUseDefaultOtlpEndpoint() {
      OtlpExporterConfig config = properties.getExporter();
      assertThat(config.getEndpoint()).isEqualTo("http://localhost:4317");
    }

    /// 测试默认超时配置。
    @Test
    @DisplayName("应该使用默认超时时间")
    void shouldUseDefaultTimeout() {
      OtlpExporterConfig config = properties.getExporter();
      assertThat(config.getTimeout()).isEqualTo(Duration.ofSeconds(10));
    }

    /// 测试默认压缩配置。
    @Test
    @DisplayName("应该使用默认 GZIP 压缩")
    void shouldUseDefaultGzipCompression() {
      OtlpExporterConfig config = properties.getExporter();
      assertThat(config.getCompression()).isEqualTo(OtlpExporterConfig.Compression.GZIP);
    }

    /// 测试 OpenTelemetry SDK 实例类型。
    @Test
    @DisplayName("应该创建 OpenTelemetrySdk 实例")
    void shouldCreateOpenTelemetrySdkInstance() {
      assertThat(openTelemetry).isInstanceOf(OpenTelemetrySdk.class);
    }

    /// 测试 Resource 包含服务名称。
    @Test
    @DisplayName("Resource 应该包含服务名称")
    void shouldResourceContainServiceName() {
      assertThat(otelResource.getAttribute(io.opentelemetry.semconv.ServiceAttributes.SERVICE_NAME))
          .isEqualTo("otlp-test-app");
    }

    /// 测试所有导出器 Bean 创建。
    @Test
    @DisplayName("应该创建所有 OTLP 导出器")
    void shouldCreateAllOtlpExporters() {
      assertThat(otlpGrpcSpanExporter).isNotNull();
      assertThat(otlpGrpcLogRecordExporter).isNotNull();
    }

    /// 测试 TracerProvider 和 LoggerProvider。
    @Test
    @DisplayName("应该创建 TracerProvider 和 LoggerProvider")
    void shouldCreateProviders() {
      assertThat(sdkTracerProvider).isNotNull();
      assertThat(sdkLoggerProvider).isNotNull();
    }
  }

  ///
  /// 自定义配置测试场景。
  ///
  /// 验证自定义 OTLP 配置正确绑定。
  ///
  @Nested
  @DisplayName("自定义配置")
  @SpringBootTest(classes = OtlpExporterIT.TestConfiguration.class)
  @TestPropertySource(
      properties = {
        "spring.application.name=custom-otlp-app",
        "patra.observability.enabled=true",
        "patra.observability.application-name=my-custom-service",
        "patra.observability.environment=prod",
        "patra.observability.cluster=cluster-1",
        "patra.observability.exporter.enabled=true",
        "patra.observability.exporter.endpoint=http://otel-collector:4317",
        "patra.observability.exporter.timeout=30s",
        "patra.observability.exporter.compression=NONE",
        "management.observations.annotations.enabled=false"
      })
  class CustomConfigTests {

    @Autowired private ObservabilityProperties properties;

    @Autowired private Resource otelResource;

    /// 测试自定义端点配置。
    @Test
    @DisplayName("应该使用自定义 OTLP 端点")
    void shouldUseCustomOtlpEndpoint() {
      OtlpExporterConfig config = properties.getExporter();
      assertThat(config.getEndpoint()).isEqualTo("http://otel-collector:4317");
    }

    /// 测试自定义超时配置。
    @Test
    @DisplayName("应该使用自定义超时时间")
    void shouldUseCustomTimeout() {
      OtlpExporterConfig config = properties.getExporter();
      assertThat(config.getTimeout()).isEqualTo(Duration.ofSeconds(30));
    }

    /// 测试自定义压缩配置。
    @Test
    @DisplayName("应该使用 NONE 压缩")
    void shouldUseNoCompression() {
      OtlpExporterConfig config = properties.getExporter();
      assertThat(config.getCompression()).isEqualTo(OtlpExporterConfig.Compression.NONE);
    }

    /// 测试自定义服务名称优先于 spring.application.name。
    @Test
    @DisplayName("应该优先使用自定义应用名称")
    void shouldPrioritizeCustomApplicationName() {
      assertThat(otelResource.getAttribute(io.opentelemetry.semconv.ServiceAttributes.SERVICE_NAME))
          .isEqualTo("my-custom-service");
    }

    /// 测试环境和集群属性。
    @Test
    @DisplayName("应该包含环境和集群属性")
    void shouldContainEnvironmentAndCluster() {
      assertThat(properties.getEnvironment()).isEqualTo("prod");
      assertThat(properties.getCluster()).isEqualTo("cluster-1");
    }
  }

  ///
  /// 日志禁用测试场景。
  ///
  /// 验证禁用日志时，LoggerProvider 不被创建。
  ///
  @Nested
  @DisplayName("日志禁用")
  @SpringBootTest(classes = OtlpExporterIT.TestConfiguration.class)
  @TestPropertySource(
      properties = {
        "patra.observability.enabled=true",
        "patra.observability.exporter.enabled=true",
        "patra.observability.logging.enabled=false",
        "management.observations.annotations.enabled=false"
      })
  class LoggingDisabledTests {

    @Autowired private ApplicationContext context;

    @Autowired private OpenTelemetry openTelemetry;

    /// 测试禁用日志时不创建 LoggerProvider。
    @Test
    @DisplayName("不应该创建 SdkLoggerProvider")
    void shouldNotCreateLoggerProvider() {
      String[] beanNames = context.getBeanNamesForType(SdkLoggerProvider.class);
      assertThat(beanNames).isEmpty();
    }

    /// 测试禁用日志时不创建 LogRecordExporter。
    @Test
    @DisplayName("不应该创建 OtlpGrpcLogRecordExporter")
    void shouldNotCreateLogRecordExporter() {
      String[] beanNames = context.getBeanNamesForType(OtlpGrpcLogRecordExporter.class);
      assertThat(beanNames).isEmpty();
    }

    /// 测试 OpenTelemetry 仍然可用。
    @Test
    @DisplayName("OpenTelemetry 应该仍然可用")
    void shouldStillHaveOpenTelemetry() {
      assertThat(openTelemetry).isNotNull();
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
    // 使用自动配置
  }
}
