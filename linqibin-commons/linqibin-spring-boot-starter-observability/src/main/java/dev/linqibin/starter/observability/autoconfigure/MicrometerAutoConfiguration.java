package dev.linqibin.starter.observability.autoconfigure;

import dev.linqibin.starter.observability.config.ObservabilityProperties;
import dev.linqibin.starter.observability.filter.CommonTagsMeterFilter;
import dev.linqibin.starter.observability.filter.HighCardinalityMeterFilter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/// Micrometer 自动配置。
///
/// 负责配置 Micrometer 相关组件：
///
/// - **OTel Agent MeterRegistry 桥接**：将 Agent 注入的 `OpenTelemetryMeterRegistry` 暴露为 Spring Bean
/// - MeterFilter 注册（命名规范、公共标签、高基数过滤）
///
/// ## OTel Agent 桥接机制
///
/// **问题**：OTel Agent 将 `OpenTelemetryMeterRegistry` 注册到 `Metrics.globalRegistry`，
/// 但 Spring 使用 ApplicationContext 中的 MeterRegistry Bean。这导致 Spring 组件
/// （如 Spring Batch、HikariCP）的指标无法被 Agent 采集。
///
/// **解决方案**：从 `globalRegistry` 中提取 `OpenTelemetryMeterRegistry`，作为 Primary Bean 暴露。
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
    prefix = "linqibin.starter.observability.metrics",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class MicrometerAutoConfiguration {

  private static final Logger log = LoggerFactory.getLogger(MicrometerAutoConfiguration.class);

  /// 构造函数。
  public MicrometerAutoConfiguration() {
    log.info("初始化 Micrometer 自动配置");
  }

  // ==================== OTel Agent MeterRegistry 桥接 ====================

  /// 桥接 OTel Agent 的 MeterRegistry 到 Spring ApplicationContext。
  ///
  /// **问题背景**：
  ///
  /// OTel Agent 将 `OpenTelemetryMeterRegistry` 注册到 `Metrics.globalRegistry`，
  /// 但 Spring 使用 ApplicationContext 中的 MeterRegistry Bean。
  /// 这导致 Spring 组件（如 Spring Batch、HikariCP）的指标无法被 Agent 采集。
  ///
  /// **解决方案**：
  ///
  /// 从 globalRegistry 中提取 `OpenTelemetryMeterRegistry`，作为 Primary Bean 暴露，
  /// 覆盖 Spring Boot Actuator 默认创建的 `SimpleMeterRegistry`。
  ///
  /// **生效条件**：
  ///
  /// - 类路径中存在 OTel Java Agent（检测 `OpenTelemetryAgent` 类）
  /// - 配置了 `otel.instrumentation.micrometer.enabled=true`
  ///
  /// @return OTel MeterRegistry
  /// @throws IllegalStateException 如果 OTel Agent 已配置但找不到 OpenTelemetryMeterRegistry
  @Bean
  @Primary
  @ConditionalOnClass(name = "io.opentelemetry.javaagent.OpenTelemetryAgent")
  @ConditionalOnProperty(name = "otel.instrumentation.micrometer.enabled", havingValue = "true")
  public MeterRegistry otelMeterRegistryBridge() {
    MeterRegistry otelRegistry =
        Metrics.globalRegistry.getRegistries().stream()
            .filter(r -> r.getClass().getName().contains("OpenTelemetryMeterRegistry"))
            .findAny()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "OTel Agent 已配置 micrometer 桥接，但未找到 OpenTelemetryMeterRegistry。"
                            + "请确保使用 -javaagent 参数正确加载 OTel Java Agent"));

    // 从 globalRegistry 移除，避免重复计数
    Metrics.globalRegistry.remove(otelRegistry);
    log.info(
        "成功桥接 OTel Agent MeterRegistry [{}] 到 Spring ApplicationContext",
        otelRegistry.getClass().getSimpleName());
    return otelRegistry;
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
  /// - applicationName：`linqibin.starter.observability.application-name` >
  // `spring.application.name`
  /// - environment：`linqibin.starter.observability.environment` >
  // `spring.profiles.active`（"default" 映射为
  // "dev"）
  ///
  /// @param properties          可观测性配置属性
  /// @param springAppName       Spring 应用名称（自动 fallback）
  /// @param springActiveProfile Spring 激活的 profile（自动 fallback）
  /// @return CommonTagsMeterFilter 实例
  @Bean
  @Order(Ordered.LOWEST_PRECEDENCE) // 最后执行
  public CommonTagsMeterFilter commonTagsMeterFilter(
      ObservabilityProperties properties,
      @Value("${spring.application.name:}") String springAppName,
      @Value("${spring.profiles.active:default}") String springActiveProfile) {
    // applicationName 优先级：显式配置 > spring.application.name
    String applicationName = properties.getApplicationName();
    if (applicationName == null || applicationName.isEmpty()) {
      applicationName = springAppName;
    }
    // environment 优先级：显式配置 > spring.profiles.active（"default" 映射为 "dev"）
    String environment = properties.getEnvironment();
    if (environment == null || environment.isEmpty()) {
      environment = "default".equals(springActiveProfile) ? "dev" : springActiveProfile;
    }
    return new CommonTagsMeterFilter(
        applicationName,
        environment,
        properties.getRegion(),
        properties.getCluster(),
        properties.getMetrics().getCommonTags());
  }
}
