package com.patra.starter.observability.config;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/// Patra 可观测性统一配置属性。
///
/// 架构说明：
///
/// - **Tracing**: 由 OTel Java Agent 通过 `-javaagent` 参数自动处理（零代码侵入）
/// - **Metrics**: 由 OTel Agent + Micrometer Bridge 导出到 OTel Collector
/// - **Logging**: 由 OTel Agent 自动注入 `trace_id`/`span_id` 到 MDC，Logback 自动格式化输出
///
/// 本配置包含 Metrics 相关配置，Tracing/Logging 由 Agent 自动处理无需配置。
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

  /// 环境标识（默认自动从 spring.profiles.active 获取，"default" 映射为 "dev"）。
  private String environment;

  /// 区域标识（cn-east-1, us-west-2）。
  private String region;

  /// 集群标识。
  private String cluster = "default";

  /// 指标配置。
  private MetricsConfig metrics = new MetricsConfig();

  /// 指标配置。
  @Data
  public static class MetricsConfig {
    /// 是否启用指标收集。
    private boolean enabled = true;

    /// 公共标签（自动添加到所有指标）。
    private Map<String, String> commonTags = new HashMap<>();
  }
}
