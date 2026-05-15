package com.patra.starter.observability.filter;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// 公共标签过滤器。
///
/// 功能：
///
/// - 自动为所有 Meter 添加公共标签
/// - 添加系统标签：application、environment、region、cluster
/// - 添加用户自定义标签
///
/// 使用场景：
///
/// - 统一标签体系：确保所有指标都包含核心标识标签
/// - 多环境区分：通过 environment、region 区分不同环境的指标
/// - 多集群管理：通过 cluster 标签区分不同集群
///
/// @author Jobs
/// @since 1.0.0
public class CommonTagsMeterFilter implements MeterFilter {

  private static final Logger log = LoggerFactory.getLogger(CommonTagsMeterFilter.class);

  private final Map<String, String> commonTags;

  /// 构造函数。
  ///
  /// @param applicationName 应用名称
  /// @param environment     环境标识（dev、staging、prod）
  /// @param region          区域标识
  /// @param cluster         集群标识
  /// @param customTags      用户自定义标签
  public CommonTagsMeterFilter(
      String applicationName,
      String environment,
      String region,
      String cluster,
      Map<String, String> customTags) {
    this.commonTags = new HashMap<>();

    // 添加系统标签
    if (applicationName != null && !applicationName.isEmpty()) {
      this.commonTags.put("application", applicationName);
    }
    if (environment != null && !environment.isEmpty()) {
      this.commonTags.put("environment", environment);
    }
    if (region != null && !region.isEmpty()) {
      this.commonTags.put("region", region);
    }
    if (cluster != null && !cluster.isEmpty()) {
      this.commonTags.put("cluster", cluster);
    }

    // 添加用户自定义标签
    if (customTags != null && !customTags.isEmpty()) {
      this.commonTags.putAll(customTags);
    }

    log.info("初始化公共标签过滤器，标签数量: {}, 标签: {}", this.commonTags.size(), this.commonTags);
  }

  /// 为 Meter 添加公共标签。
  ///
  /// @param id Meter ID
  /// @return 添加公共标签后的 Meter ID
  @Override
  public Meter.Id map(Meter.Id id) {
    // 为 Meter 添加所有公共标签
    for (Map.Entry<String, String> entry : commonTags.entrySet()) {
      // 检查标签是否已存在，避免覆盖
      if (id.getTag(entry.getKey()) == null) {
        id = id.withTag(Tag.of(entry.getKey(), entry.getValue()));
      }
    }
    return id;
  }
}
