package com.patra.starter.observability.autoconfigure;

import com.patra.starter.observability.config.ObservabilityProperties;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/// Prometheus Meter Registry 自动配置。
///
/// 当满足以下条件时启用：
///
/// - 类路径中存在 PrometheusMeterRegistry
/// - 配置 patra.observability.metrics.prometheus.enabled=true
///
/// 功能：
///
/// - 配置 Prometheus Meter Registry（复用 Spring Boot Actuator 自动配置）
/// - 配置 Exemplars 支持（与 Tracing 关联）
/// - 提供 Prometheus 端点
///
/// 注意：
///
/// - Prometheus MeterRegistry 由 Spring Boot Actuator 自动配置
/// - 本配置类主要提供 Patra 特定的配置和扩展
/// - 确保添加 spring-boot-starter-actuator 依赖
///
/// @author Jobs
/// @since 1.0.0
@AutoConfiguration(after = MicrometerAutoConfiguration.class)
@ConditionalOnClass(PrometheusMeterRegistry.class)
@EnableConfigurationProperties(ObservabilityProperties.class)
@ConditionalOnProperty(
    prefix = "patra.observability.metrics.prometheus",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class PrometheusAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PrometheusAutoConfiguration.class);

    /// 构造函数。
    ///
    /// @param properties 可观测性配置属性
    public PrometheusAutoConfiguration(ObservabilityProperties properties) {
        ObservabilityProperties.PrometheusConfig config = properties.getMetrics().getPrometheus();

        log.info("初始化 Prometheus 自动配置 [Exemplars启用: {}]",
            config.isEnableExemplars());

        if (config.isEnableExemplars()) {
            log.info("Exemplars 已启用，Prometheus 指标将关联 Tracing 信息");
        }
    }

    // TODO: 如果需要自定义 PrometheusMeterRegistry 配置，可以在此处添加
    // Spring Boot Actuator 已经提供了默认的 PrometheusMeterRegistry 自动配置
    // 如果需要自定义，可以通过 MeterRegistryCustomizer<PrometheusMeterRegistry> 实现
}
