package com.patra.starter.observability.filter;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// 指标命名规范过滤器。
///
/// 功能：
///
/// - 强制执行 Patra 指标命名规范：patra.{module}.{metric}
/// - 自动添加 "patra." 前缀（如果缺失）
/// - 转换为小写，替换非法字符为下划线
/// - 应用可选的指标前缀配置
///
/// 命名规范：
///
/// - 格式：patra.{module}.{metric}
/// - 正则：^patra\.[a-z0-9_]+(\.[a-z0-9_]+)*$
/// - 只能包含小写字母、数字、下划线、点号
///
/// 使用场景：
///
/// - 统一命名：确保所有指标遵循一致的命名规范
/// - 自动修正：自动修正不符合规范的指标名称
/// - 防止混乱：避免因命名不一致导致的查询困难
///
/// @author Jobs
/// @since 1.0.0
public class MetricNamingMeterFilter implements MeterFilter {

  private static final Logger log = LoggerFactory.getLogger(MetricNamingMeterFilter.class);

  /// Patra 指标命名规范正则。
  /// 格式：patra.{module}.{metric}
  /// 规则：只能包含小写字母、数字、下划线、点号
  private static final Pattern VALID_PATTERN =
      Pattern.compile("^patra\\.[a-z0-9_]+(\\.[a-z0-9_]+)*$");

  /// Patra 前缀。
  private static final String PATRA_PREFIX = "patra.";

  private final String customPrefix;

  /// 构造函数。
  ///
  /// @param customPrefix 可选的自定义前缀（在 "patra." 之后添加）
  public MetricNamingMeterFilter(String customPrefix) {
    this.customPrefix = (customPrefix != null && !customPrefix.isEmpty()) ? customPrefix : null;
    log.info("初始化指标命名规范过滤器，自定义前缀: {}", this.customPrefix != null ? this.customPrefix : "无");
  }

  /// 规范化指标名称。
  ///
  /// @param id Meter ID
  /// @return 规范化后的 Meter ID
  @Override
  public Meter.Id map(Meter.Id id) {
    String originalName = id.getName();
    String normalizedName = normalizeName(originalName);

    if (!originalName.equals(normalizedName)) {
      log.debug("指标名称已规范化: {} -> {}", originalName, normalizedName);
      return id.withName(normalizedName);
    }

    return id;
  }

  /// 规范化指标名称。
  ///
  /// 规范化步骤：
  ///
  /// - 转换为小写
  /// - 替换非法字符为下划线
  /// - 添加 "patra." 前缀（如果缺失）
  /// - 应用自定义前缀（如果配置）
  /// - 去除多余的点号和下划线
  ///
  /// @param name 原始指标名称
  /// @return 规范化后的指标名称
  private String normalizeName(String name) {
    if (name == null || name.isEmpty()) {
      return "patra.unknown";
    }

    // 1. 转换为小写
    String normalized = name.toLowerCase();

    // 2. 替换非法字符为下划线（保留 a-z、0-9、_、.）
    normalized = normalized.replaceAll("[^a-z0-9_.]", "_");

    // 3. 去除开头和结尾的点号和下划线
    normalized = normalized.replaceAll("^[_.]+|[_.]+$", "");

    // 4. 去除连续的点号和下划线
    normalized = normalized.replaceAll("[_.]{2,}", ".");

    // 5. 添加 "patra." 前缀（如果缺失）
    if (!normalized.startsWith(PATRA_PREFIX)) {
      normalized = PATRA_PREFIX + normalized;
    }

    // 6. 应用自定义前缀（在 "patra." 之后）
    if (customPrefix != null) {
      // 如果已经包含自定义前缀，不重复添加
      String expectedPrefix = PATRA_PREFIX + customPrefix + ".";
      if (!normalized.startsWith(expectedPrefix)) {
        // 移除 "patra." 后重新组合
        String suffix = normalized.substring(PATRA_PREFIX.length());
        normalized = PATRA_PREFIX + customPrefix + "." + suffix;
      }
    }

    // 7. 最终验证（如果仍不符合规范，使用默认名称）
    if (!VALID_PATTERN.matcher(normalized).matches()) {
      log.warn("指标名称规范化失败，使用默认名称: {} -> patra.unknown", name);
      return "patra.unknown";
    }

    return normalized;
  }
}
