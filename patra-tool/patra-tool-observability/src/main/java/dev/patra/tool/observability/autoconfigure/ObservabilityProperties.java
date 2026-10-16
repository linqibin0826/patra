package dev.patra.tool.observability.autoconfigure;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/// 可观测性配置，前缀 `patra.observability`。
///
/// @param enabled 总开关
/// @param applicationName 指标公共标签 application（空则用 `spring.application.name`）
/// @param environment 环境标签
/// @param region 区域标签
/// @param cluster 集群标签
/// @param metrics 指标
@Validated
@ConfigurationProperties(prefix = "patra.observability")
public record ObservabilityProperties(
    @DefaultValue("true") boolean enabled,
    String applicationName,
    String environment,
    String region,
    @DefaultValue("default") String cluster,
    @DefaultValue Metrics metrics) {

  /// 指标开关与公共标签。
  ///
  /// @param enabled 是否注册 MeterFilter
  /// @param commonTags 附加到所有指标的公共标签
  public record Metrics(
      @DefaultValue("true") boolean enabled, @DefaultValue Map<String, String> commonTags) {
    public Metrics {
      commonTags = Map.copyOf(commonTags);
    }
  }
}
