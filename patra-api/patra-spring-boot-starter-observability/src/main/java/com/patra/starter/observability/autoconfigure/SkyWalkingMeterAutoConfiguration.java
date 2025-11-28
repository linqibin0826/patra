package com.patra.starter.observability.autoconfigure;

import com.patra.starter.observability.config.ObservabilityProperties;
import org.apache.skywalking.apm.meter.micrometer.SkywalkingMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/// SkyWalking Meter Registry 自动配置。
///
/// 当满足以下条件时启用：
///
/// - 类路径中存在 SkywalkingMeterRegistry
/// - 配置 patra.observability.metrics.skywalking.enabled=true
///
/// 功能：
///
/// - 创建 SkywalkingMeterRegistry Bean
/// - 配置 SkyWalking OAP 服务器地址
/// - 配置指标导出间隔
///
/// @author Jobs
/// @since 1.0.0
@AutoConfiguration(after = MicrometerAutoConfiguration.class)
@ConditionalOnClass(SkywalkingMeterRegistry.class)
@EnableConfigurationProperties(ObservabilityProperties.class)
@ConditionalOnProperty(
    prefix = "patra.observability.metrics.skywalking",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class SkyWalkingMeterAutoConfiguration {

  private static final Logger log = LoggerFactory.getLogger(SkyWalkingMeterAutoConfiguration.class);

  /// 创建 SkyWalking Meter Registry。
  ///
  /// SkyWalking Meter Registry 会将 Micrometer 收集的指标转发到 SkyWalking OAP 服务器。
  ///
  /// @param properties 可观测性配置属性
  /// @return SkywalkingMeterRegistry 实例
  @Bean
  @ConditionalOnMissingBean(SkywalkingMeterRegistry.class)
  public SkywalkingMeterRegistry skywalkingMeterRegistry(ObservabilityProperties properties) {
    ObservabilityProperties.SkyWalkingMeterConfig config = properties.getMetrics().getSkywalking();

    log.info(
        "创建 SkyWalking Meter Registry [OAP地址: {}, 导出间隔: {}]",
        config.getOapAddress(),
        properties.getMetrics().getStep());

    // 使用无参构造函数创建 SkywalkingMeterRegistry
    // SkyWalking Agent 会自动连接到配置的 OAP 服务器
    return new SkywalkingMeterRegistry();
  }
}
