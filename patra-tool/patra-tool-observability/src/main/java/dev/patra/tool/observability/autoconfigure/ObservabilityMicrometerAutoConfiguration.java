package dev.patra.tool.observability.autoconfigure;

import dev.patra.tool.core.PatraOrders;
import dev.patra.tool.observability.filter.CommonTagsMeterFilter;
import dev.patra.tool.observability.filter.HighCardinalityMeterFilter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;

/// Micrometer 自动配置：注册高基数标签过滤与公共标签两个 `MeterFilter`。
///
/// 指标由 Boot 的 `OtlpMeterRegistry` 推给 OTel Collector（导出地址见 `ObservabilityDefaultsPostProcessor`），
/// 本机运行时挂的 OTel Agent 只管链路与日志，不导出指标。
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
public final class ObservabilityMicrometerAutoConfiguration {

  private static final Logger log =
      LoggerFactory.getLogger(ObservabilityMicrometerAutoConfiguration.class);

  /// 构造函数。
  public ObservabilityMicrometerAutoConfiguration() {
    log.info("初始化 Micrometer 自动配置");
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
  @Order(PatraOrders.METER_FILTER_HIGH_CARDINALITY)
  public HighCardinalityMeterFilter highCardinalityMeterFilter() {
    // Spring Boot 会自动将 MeterFilter Bean 应用到 MeterRegistry
    return new HighCardinalityMeterFilter();
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
  /// 自动获取优先级：
  ///
  /// - applicationName：`patra.observability.application-name` >
  // `spring.application.name`
  /// - environment：`patra.observability.environment` >
  // `spring.profiles.active`（"default" 映射为
  // "dev"）
  ///
  /// @param properties          可观测性配置属性
  /// @param springAppName       Spring 应用名称（自动 fallback）
  /// @param springActiveProfile Spring 激活的 profile（自动 fallback）
  /// @return CommonTagsMeterFilter 实例
  @Bean
  @Order(PatraOrders.METER_FILTER_COMMON_TAGS)
  public CommonTagsMeterFilter commonTagsMeterFilter(
      ObservabilityProperties properties,
      @Value("${spring.application.name:}") String springAppName,
      @Value("${spring.profiles.active:default}") String springActiveProfile) {
    // applicationName 优先级：显式配置 > spring.application.name
    String applicationName = properties.applicationName();
    if (applicationName == null || applicationName.isEmpty()) {
      applicationName = springAppName;
    }
    // environment 优先级：显式配置 > spring.profiles.active（"default" 映射为 "dev"）
    String environment = properties.environment();
    if (environment == null || environment.isEmpty()) {
      environment = "default".equals(springActiveProfile) ? "dev" : springActiveProfile;
    }
    return new CommonTagsMeterFilter(
        applicationName,
        environment,
        properties.region(),
        properties.cluster(),
        properties.metrics().commonTags());
  }
}
