package com.patra.starter.observability.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/// Patra 可观测性统一配置属性。
///
/// 提供可观测性三大支柱的配置：
///
/// - Metrics（指标）：指标收集、导出、命名规范
/// - Tracing（追踪）：分布式追踪、采样率、Baggage 传播
/// - Logging（日志）：日志关联、TraceID 包含、日志格式
///
/// 安全特性：
///
/// - 敏感数据脱敏：生产环境强制启用
/// - Actuator 访问控制：保护可观测性端点
///
/// @author Jobs
/// @since 1.0.0
@ConfigurationProperties(prefix = "patra.observability")
@Data
public class ObservabilityProperties {

  /// 全局开关（默认启用）。
  private boolean enabled = true;

  /// 应用标识（建议从 spring.application.name 自动获取）。
  private String applicationName;

  /// 环境标识（dev, prod）。
  private String environment = "dev";

  /// 区域标识（cn-east-1, us-west-2）。
  private String region;

  /// 集群标识。
  private String cluster = "default";

  /// 指标配置。
  private MetricsConfig metrics = new MetricsConfig();

  /// 追踪配置。
  private TracingConfig tracing = new TracingConfig();

  /// 日志配置。
  private LoggingConfig logging = new LoggingConfig();

  /// ObservationHandler 配置。
  private HandlersConfig handlers = new HandlersConfig();

  /// 安全配置（敏感数据脱敏）。
  private SecurityConfig security = new SecurityConfig();

  /// 指标配置。
  @Data
  public static class MetricsConfig {
    ///
    /// 是否启用指标收集。

    private boolean enabled = true;

    ///
    /// 指标前缀（可选，默认为空）。

    private String prefix = "";

    ///
    /// 公共标签（自动添加到所有指标）。

    private Map<String, String> commonTags = new HashMap<>();

    ///
    /// 导出间隔。

    @NotNull(message = "导出间隔不能为 null")
    private Duration step = Duration.ofSeconds(60);

    ///
    /// SkyWalking Meter Registry 配置。

    private SkyWalkingMeterConfig skywalking = new SkyWalkingMeterConfig();

    ///
    /// Prometheus Registry 配置。

    private PrometheusConfig prometheus = new PrometheusConfig();
  }

  ///
  /// SkyWalking Meter 配置。

  @Data
  public static class SkyWalkingMeterConfig {
    ///
    /// 是否启用。

    private boolean enabled = true;

    ///
    /// OAP 服务器地址。

    private String oapAddress = "skywalking-oap:11800";
  }

  ///
  /// Prometheus 配置。

  @Data
  public static class PrometheusConfig {
    ///
    /// 是否启用。

    private boolean enabled = true;

    ///
    /// 是否启用 Exemplars（与 Tracing 关联）。

    private boolean enableExemplars = true;
  }

  ///
  /// 追踪配置。

  @Data
  public static class TracingConfig {
    ///
    /// 是否启用追踪。

    private boolean enabled = true;

    ///
    /// 采样率（0.0 - 1.0）。

    @DecimalMin(value = "0.0", message = "采样率必须 >= 0.0")
    @DecimalMax(value = "1.0", message = "采样率必须 <= 1.0")
    private double samplingRate = 1.0;

    ///
    /// Baggage 传播字段。

    private List<String> baggageFields = List.of("X-Request-Id", "X-Correlation-Id");

    ///
    /// 追踪 Header 名称。

    private List<String> headerNames = List.of("X-Trace-ID", "X-B3-TraceId", "traceparent");
  }

  ///
  /// 日志配置。

  @Data
  public static class LoggingConfig {
    ///
    /// 是否启用日志关联。

    private boolean enabled = true;

    ///
    /// 是否在日志中包含 traceId。

    private boolean includeTraceId = true;

    ///
    /// 日志格式模式。

    private String pattern = "[%tid] [${spring.application.name},%X{traceId:-},%X{spanId:-}]";
  }

  ///
  /// ObservationHandler 配置。

  @Data
  public static class HandlersConfig {
    ///
    /// 日志 Handler。

    private LoggingHandlerConfig logging = new LoggingHandlerConfig();

    ///
    /// 性能 Handler。

    private PerformanceHandlerConfig performance = new PerformanceHandlerConfig();

    ///
    /// 告警 Handler（未来扩展）。

    private AlertingHandlerConfig alerting = new AlertingHandlerConfig();
  }

  ///
  /// 日志 Handler 配置。

  @Data
  public static class LoggingHandlerConfig {
    ///
    /// 是否启用。

    private boolean enabled = true;

    ///
    /// 日志级别。

    private String logLevel = "DEBUG";
  }

  ///
  /// 性能 Handler 配置。

  @Data
  public static class PerformanceHandlerConfig {
    ///
    /// 是否启用。

    private boolean enabled = true;

    ///
    /// 慢操作阈值。

    @NotNull(message = "慢操作阈值不能为 null")
    private Duration slowThreshold = Duration.ofSeconds(3);
  }

  ///
  /// 告警 Handler 配置（未来扩展）。

  @Data
  public static class AlertingHandlerConfig {
    ///
    /// 是否启用。

    private boolean enabled = false;
  }

  ///
  /// 安全配置（敏感数据脱敏）。

  @Data
  public static class SecurityConfig {
    ///
    /// 是否启用敏感数据脱敏（生产环境强制启用）。

    private boolean maskSensitiveData = true;

    ///
    /// 自定义敏感数据模式（正则表达式）。

    private List<String> sensitivePatterns = new ArrayList<>();

    ///
    /// 允许在哪些环境中禁用脱敏（仅用于调试，生产环境强制启用）。

    private List<String> maskingDisabledInEnvironments = List.of("dev-local");
  }
}
