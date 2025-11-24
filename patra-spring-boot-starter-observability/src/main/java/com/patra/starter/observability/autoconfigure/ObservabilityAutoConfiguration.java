package com.patra.starter.observability.autoconfigure;

import com.patra.starter.observability.config.ObservabilityProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/// Patra 可观测性自动配置。
///
/// 提供统一的可观测性基础设施，包括：
///
/// - ObservationRegistry 配置和 Handler 注册
/// - 公共标签配置
/// - @Observed 注解支持
/// - 命名规范配置
///
/// @author Jobs
/// @since 1.0.0
@AutoConfiguration
@ConditionalOnClass({ObservationRegistry.class, MeterRegistry.class})
@EnableConfigurationProperties(ObservabilityProperties.class)
@ConditionalOnProperty(
    prefix = "patra.observability",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class ObservabilityAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityAutoConfiguration.class);

    /// 构造函数。
    ///
    /// @param properties 可观测性配置属性
    public ObservabilityAutoConfiguration(ObservabilityProperties properties) {
        log.info("初始化 Patra 可观测性自动配置 [环境: {}, 应用: {}]",
            properties.getEnvironment(),
            properties.getApplicationName() != null ? properties.getApplicationName() : "未配置");
    }

    /// 创建或注入 ObservationRegistry。
    ///
    /// ObservationRegistry 是 Micrometer Observation API 的核心，
    /// 用于创建和管理 Observation 实例。
    ///
    /// @return ObservationRegistry 实例
    @Bean
    @ConditionalOnMissingBean
    public ObservationRegistry observationRegistry() {
        log.info("创建 ObservationRegistry");
        return ObservationRegistry.create();
    }

    /// 启用 @Observed 注解支持。
    ///
    /// 通过 AOP 拦截标注 @Observed 的方法，自动创建 Observation。
    ///
    /// @param observationRegistry ObservationRegistry 实例
    /// @return ObservedAspect 实例
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "management.observations.annotations",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    public ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        log.info("启用 @Observed 注解支持");
        return new ObservedAspect(observationRegistry);
    }
}
