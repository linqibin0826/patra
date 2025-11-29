package com.patra.starter.observability.autoconfigure;

import com.patra.starter.observability.config.ObservabilityProperties;
import com.patra.starter.observability.filter.CommonTagsMeterFilter;
import com.patra.starter.observability.filter.HighCardinalityMeterFilter;
import com.patra.starter.observability.filter.MetricNamingMeterFilter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/// Micrometer 自动配置。
///
/// 负责配置 Micrometer 相关组件：
///
/// - MeterFilter 注册（命名规范、公共标签、高基数过滤）
///
/// 注意：
///
/// - Tracing 由 OTel Java Agent 自动处理，无需额外的 ObservationHandler
/// - ObservationFilter 已移除（公共标签由 MeterFilter 统一处理）
///
/// @author Jobs
/// @since 1.0.0
@AutoConfiguration(after = ObservabilityAutoConfiguration.class)
@ConditionalOnClass({MeterRegistry.class, ObservationRegistry.class})
@EnableConfigurationProperties(ObservabilityProperties.class)
@ConditionalOnProperty(
    prefix = "patra.observability.metrics",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class MicrometerAutoConfiguration {

  private static final Logger log = LoggerFactory.getLogger(MicrometerAutoConfiguration.class);

  /// 构造函数。
  ///
  /// @param properties 可观测性配置属性
  public MicrometerAutoConfiguration(ObservabilityProperties properties) {
    log.info(
        "初始化 Micrometer 自动配置 [指标前缀: {}]",
        properties.getMetrics().getPrefix().isEmpty() ? "无" : properties.getMetrics().getPrefix());
  }

  // ==================== MeterFilter ====================

  /// 创建高基数标签过滤器。
  ///
  /// 功能：
  ///
  /// - 过滤高基数标签（userId、requestId、traceId 等）
  /// - 防止时序数据库性能问题（时序爆炸）
  /// - 支持自定义高基数标签黑名单
  ///
  /// 执行顺序：最先执行（HIGHEST_PRECEDENCE），在其他 MeterFilter 之前移除高基数标签。
  ///
  /// @return HighCardinalityMeterFilter 实例
  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE) // 最先执行
  public HighCardinalityMeterFilter highCardinalityMeterFilter() {
    // Spring Boot 会自动将 MeterFilter Bean 应用到 MeterRegistry
    return new HighCardinalityMeterFilter();
  }

  /// 创建指标命名规范过滤器。
  ///
  /// 功能：
  ///
  /// - 强制执行 Patra 指标命名规范：patra.{module}.{metric}
  /// - 自动添加 "patra." 前缀（如果缺失）
  /// - 转换为小写，替换非法字符为下划线
  /// - 应用可选的指标前缀配置
  ///
  /// 执行顺序：中间执行，在高基数过滤后、公共标签添加前规范命名。
  ///
  /// @param properties 可观测性配置属性
  /// @return MetricNamingMeterFilter 实例
  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE + 1) // 第二执行
  public MetricNamingMeterFilter metricNamingMeterFilter(ObservabilityProperties properties) {
    String customPrefix = properties.getMetrics().getPrefix();
    return new MetricNamingMeterFilter(customPrefix);
  }

  /// 创建公共标签过滤器。
  ///
  /// 功能：
  ///
  /// - 自动为所有 Meter 添加公共标签
  /// - 添加系统标签：application、environment、region、cluster
  /// - 添加用户自定义标签
  ///
  /// 执行顺序：最后执行（LOWEST_PRECEDENCE），在标签和名称都规范后添加公共标签。
  ///
  /// @param properties 可观测性配置属性
  /// @return CommonTagsMeterFilter 实例
  @Bean
  @Order(Ordered.LOWEST_PRECEDENCE) // 最后执行
  public CommonTagsMeterFilter commonTagsMeterFilter(ObservabilityProperties properties) {
    return new CommonTagsMeterFilter(
        properties.getApplicationName(),
        properties.getEnvironment(),
        properties.getRegion(),
        properties.getCluster(),
        properties.getMetrics().getCommonTags());
  }
}
