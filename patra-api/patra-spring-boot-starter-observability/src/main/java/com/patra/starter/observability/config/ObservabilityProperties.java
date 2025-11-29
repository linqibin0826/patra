package com.patra.starter.observability.config;

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
/// 架构说明：
///
/// - **Tracing**: 由 OTel Java Agent 通过 `-javaagent` 参数自动处理（零代码侵入）
/// - **Metrics**: 由 Micrometer + Prometheus Registry 处理，Prometheus 定期 Pull
/// - **Logging**: 由 Agent 自动注入 `trace_id`/`span_id` 到 MDC
///
/// 本配置仅包含应用层需要的 Metrics 和 Handlers 配置，
/// Tracing 配置由 Agent JVM 参数控制（如 `-Dotel.traces.sampler.arg=0.1`）。
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

  /// 日志配置（日志关联）。
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
    /// Prometheus Registry 配置。

    private PrometheusConfig prometheus = new PrometheusConfig();
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
