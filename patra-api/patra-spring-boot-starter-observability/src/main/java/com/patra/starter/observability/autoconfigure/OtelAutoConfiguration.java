package com.patra.starter.observability.autoconfigure;

import com.patra.starter.observability.config.ObservabilityProperties;
import com.patra.starter.observability.config.ObservabilityProperties.OtlpExporterConfig;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.semconv.ServiceAttributes;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

///
/// OpenTelemetry 自动配置。
///
/// 提供 OpenTelemetry SDK 的自动配置，包括：
///
/// - `Resource`：服务标识（service.name, service.version）
/// - `OtlpGrpcSpanExporter`：OTLP 导出器
/// - `SdkTracerProvider`：Tracer 提供者
/// - `OpenTelemetry`：OTel SDK 入口
/// - `Tracer`：手动创建 Span（可选）
///
/// 与 OTel Java Agent 的关系：
///
/// 当使用 OTel Java Agent 时，Agent 会自动创建全局 OpenTelemetry 实例。
/// 此配置类创建的是应用级别的 OpenTelemetry Bean，用于：
/// - 不使用 Agent 的场景（仅依赖 SDK）
/// - 需要自定义 Exporter 配置的场景
///
/// @author Jobs
/// @since 1.0.0
/// @see ObservabilityProperties.OtlpExporterConfig
///
@AutoConfiguration(after = MicrometerAutoConfiguration.class)
@ConditionalOnClass(OpenTelemetry.class)
@ConditionalOnProperty(
    prefix = "patra.observability.exporter",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(ObservabilityProperties.class)
public class OtelAutoConfiguration {

  private static final Logger log = LoggerFactory.getLogger(OtelAutoConfiguration.class);

  ///
  /// 创建 OpenTelemetry Resource。
  ///
  /// Resource 包含服务的静态元数据，用于标识遥测数据的来源。
  /// 常用属性包括：service.name, service.version, service.namespace 等。
  ///
  /// @param applicationName 应用名称（来自 spring.application.name）
  /// @param properties 可观测性配置
  /// @return OTel Resource
  ///
  @Bean
  @ConditionalOnMissingBean
  public Resource otelResource(
      @Value("${spring.application.name:unknown}") String applicationName,
      ObservabilityProperties properties) {
    String serviceName =
        properties.getApplicationName() != null ? properties.getApplicationName() : applicationName;

    log.info("创建 OpenTelemetry Resource: service.name={}", serviceName);

    return Resource.getDefault()
        .merge(
            Resource.create(
                Attributes.builder()
                    .put(ServiceAttributes.SERVICE_NAME, serviceName)
                    .put(ServiceAttributes.SERVICE_VERSION, "0.1.0-SNAPSHOT")
                    .put("service.environment", properties.getEnvironment())
                    .put("service.cluster", properties.getCluster())
                    .build()));
  }

  ///
  /// 创建 OTLP gRPC Span 导出器。
  ///
  /// 通过 gRPC 协议将 Span 数据发送到 OpenTelemetry Collector。
  /// 支持配置端点、超时、压缩等参数。
  ///
  /// @param properties 可观测性配置
  /// @return OTLP gRPC Span Exporter
  ///
  @Bean
  @ConditionalOnMissingBean
  public OtlpGrpcSpanExporter otlpGrpcSpanExporter(ObservabilityProperties properties) {
    OtlpExporterConfig config = properties.getExporter();

    log.info(
        "创建 OTLP gRPC Span Exporter: endpoint={}, timeout={}s, compression={}",
        config.getEndpoint(),
        config.getTimeout().getSeconds(),
        config.getCompression());

    var builder =
        OtlpGrpcSpanExporter.builder()
            .setEndpoint(config.getEndpoint())
            .setTimeout(config.getTimeout().toMillis(), TimeUnit.MILLISECONDS);

    // 配置压缩
    if (config.getCompression() == OtlpExporterConfig.Compression.GZIP) {
      builder.setCompression("gzip");
    }

    // 配置自定义 Headers
    config.getHeaders().forEach(builder::addHeader);

    return builder.build();
  }

  ///
  /// 创建 SDK Tracer Provider。
  ///
  /// SdkTracerProvider 是创建 Tracer 的工厂，管理 Span 的生命周期。
  /// 使用 BatchSpanProcessor 批量发送 Span 以提高性能。
  ///
  /// @param resource 服务资源信息
  /// @param exporter Span 导出器
  /// @return SDK Tracer Provider
  ///
  @Bean
  @ConditionalOnMissingBean
  public SdkTracerProvider sdkTracerProvider(Resource resource, OtlpGrpcSpanExporter exporter) {
    log.info("创建 SdkTracerProvider with BatchSpanProcessor");

    return SdkTracerProvider.builder()
        .setResource(resource)
        .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
        .build();
  }

  ///
  /// 创建 OTLP gRPC Log 导出器。
  ///
  /// 通过 gRPC 协议将日志数据发送到 OpenTelemetry Collector。
  /// 用于将应用日志通过 OTLP 协议导出到 Loki。
  ///
  /// @param properties 可观测性配置
  /// @return OTLP gRPC Log Exporter
  ///
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "patra.observability.logging",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public OtlpGrpcLogRecordExporter otlpGrpcLogRecordExporter(ObservabilityProperties properties) {
    OtlpExporterConfig config = properties.getExporter();

    log.info("创建 OTLP gRPC Log Exporter: endpoint={}", config.getEndpoint());

    var builder =
        OtlpGrpcLogRecordExporter.builder()
            .setEndpoint(config.getEndpoint())
            .setTimeout(config.getTimeout().toMillis(), TimeUnit.MILLISECONDS);

    if (config.getCompression() == OtlpExporterConfig.Compression.GZIP) {
      builder.setCompression("gzip");
    }

    config.getHeaders().forEach(builder::addHeader);

    return builder.build();
  }

  ///
  /// 创建 SDK Logger Provider。
  ///
  /// SdkLoggerProvider 管理日志记录的生命周期。
  /// 使用 BatchLogRecordProcessor 批量发送日志以提高性能。
  ///
  /// @param resource 服务资源信息
  /// @param logExporter 日志导出器（可选）
  /// @return SDK Logger Provider
  ///
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "patra.observability.logging",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public SdkLoggerProvider sdkLoggerProvider(
      Resource resource, OtlpGrpcLogRecordExporter logExporter) {
    log.info("创建 SdkLoggerProvider with BatchLogRecordProcessor");

    return SdkLoggerProvider.builder()
        .setResource(resource)
        .addLogRecordProcessor(BatchLogRecordProcessor.builder(logExporter).build())
        .build();
  }

  ///
  /// 创建 OpenTelemetry SDK 实例。
  ///
  /// 这是 OpenTelemetry API 的入口点，用于获取 Tracer、Meter、Logger 等组件。
  /// 整合 TracerProvider 和 LoggerProvider，提供统一的遥测入口。
  ///
  /// @param tracerProvider Tracer 提供者
  /// @param loggerProviderProvider Logger 提供者（可选，通过 ObjectProvider 注入）
  /// @return OpenTelemetry SDK 实例
  ///
  @Bean
  @ConditionalOnMissingBean
  public OpenTelemetry openTelemetry(
      SdkTracerProvider tracerProvider, ObjectProvider<SdkLoggerProvider> loggerProviderProvider) {

    SdkLoggerProvider loggerProvider = loggerProviderProvider.getIfAvailable();

    log.info(
        "创建 OpenTelemetry SDK (Tracing: 已启用, Logging: {})", loggerProvider != null ? "已启用" : "未启用");

    var builder = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider);

    if (loggerProvider != null) {
      builder.setLoggerProvider(loggerProvider);
    }

    return builder.build();
  }

  ///
  /// 创建 Tracer Bean。
  ///
  /// Tracer 用于手动创建 Span，适用于需要细粒度追踪的场景。
  /// 大多数情况下，OTel Agent 或 Micrometer Observation API 会自动创建 Span。
  ///
  /// @param openTelemetry OpenTelemetry SDK 实例
  /// @return Tracer 实例
  ///
  @Bean
  @ConditionalOnMissingBean
  public Tracer otelTracer(OpenTelemetry openTelemetry) {
    return openTelemetry.getTracer("patra-observability", "0.1.0-SNAPSHOT");
  }
}
