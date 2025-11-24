package com.patra.starter.observability.autoconfigure;

import com.patra.starter.observability.config.ObservabilityProperties;
import com.patra.starter.observability.filter.CommonTagsObservationFilter;
import com.patra.starter.observability.filter.CommonTagsMeterFilter;
import com.patra.starter.observability.filter.HighCardinalityMeterFilter;
import com.patra.starter.observability.filter.MetricNamingMeterFilter;
import com.patra.starter.observability.filter.SensitiveDataObservationFilter;
import com.patra.starter.observability.handler.LoggingObservationHandler;
import com.patra.starter.observability.handler.PerformanceObservationHandler;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/// Micrometer 自动配置。
///
/// 负责配置 Micrometer 相关组件：
///
/// - MeterRegistry 配置
/// - MeterFilter 注册（命名规范、公共标签、高基数过滤）
/// - ObservationHandler 注册（日志、性能、指标）
/// - ObservationFilter 注册（敏感数据脱敏、公共标签）
///
/// 注意：具体的 Handler、Filter、MeterFilter 实现由其他配置类或任务阶段提供。
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
    matchIfMissing = true
)
public class MicrometerAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MicrometerAutoConfiguration.class);

    /// 构造函数。
    ///
    /// @param properties 可观测性配置属性
    public MicrometerAutoConfiguration(ObservabilityProperties properties) {
        log.info("初始化 Micrometer 自动配置 [指标前缀: {}]",
            properties.getMetrics().getPrefix().isEmpty() ? "无" : properties.getMetrics().getPrefix());
    }

    // ==================== ObservationFilter（任务 2.4） ====================

    /// 创建敏感数据脱敏过滤器。
    ///
    /// 功能：
    ///
    /// - 检测并脱敏密码、Token、API Key、身份证号、手机号等敏感信息
    /// - 支持自定义敏感数据模式
    /// - 生产环境强制启用
    ///
    /// @param properties 可观测性配置属性
    /// @param observationRegistry Observation 注册中心
    /// @return SensitiveDataObservationFilter 实例
    @Bean
    @ConditionalOnProperty(
        prefix = "patra.observability.security",
        name = "mask-sensitive-data",
        havingValue = "true",
        matchIfMissing = true  // 默认启用
    )
    public SensitiveDataObservationFilter sensitiveDataObservationFilter(
        ObservabilityProperties properties,
        ObservationRegistry observationRegistry
    ) {
        ObservabilityProperties.SecurityConfig config = properties.getSecurity();

        // 生产环境强制启用
        boolean enabled = config.isMaskSensitiveData();
        if ("prod".equalsIgnoreCase(properties.getEnvironment())) {
            enabled = true;
            log.warn("生产环境检测到，敏感数据脱敏已强制启用");
        }

        SensitiveDataObservationFilter filter = new SensitiveDataObservationFilter(
            enabled,
            config.getSensitivePatterns()
        );

        // 注册到 ObservationRegistry
        observationRegistry.observationConfig().observationFilter(filter);

        return filter;
    }

    /// 创建公共标签过滤器。
    ///
    /// 功能：
    ///
    /// - 自动为所有 Observation 添加公共标签
    /// - 添加系统标签：application、environment、region、cluster
    /// - 添加用户自定义标签
    ///
    /// @param properties 可观测性配置属性
    /// @param observationRegistry Observation 注册中心
    /// @return CommonTagsObservationFilter 实例
    @Bean
    public CommonTagsObservationFilter commonTagsObservationFilter(
        ObservabilityProperties properties,
        ObservationRegistry observationRegistry
    ) {
        CommonTagsObservationFilter filter = new CommonTagsObservationFilter(
            properties.getApplicationName(),
            properties.getEnvironment(),
            properties.getRegion(),
            properties.getCluster(),
            properties.getMetrics().getCommonTags()
        );

        // 注册到 ObservationRegistry
        observationRegistry.observationConfig().observationFilter(filter);

        return filter;
    }

    // ==================== ObservationHandler（任务 2.5） ====================

    /// 创建日志观测处理器。
    ///
    /// 功能：
    ///
    /// - 记录 Observation 生命周期事件到日志
    /// - 支持可配置的日志级别（DEBUG、INFO、WARN、ERROR）
    /// - 用于开发环境调试和生产环境审计
    ///
    /// @param properties 可观测性配置属性
    /// @param observationRegistry Observation 注册中心
    /// @return LoggingObservationHandler 实例
    @Bean
    @ConditionalOnProperty(
        prefix = "patra.observability.handlers.logging",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true  // 默认启用
    )
    public LoggingObservationHandler loggingObservationHandler(
        ObservabilityProperties properties,
        ObservationRegistry observationRegistry
    ) {
        ObservabilityProperties.LoggingHandlerConfig config = properties.getHandlers().getLogging();

        LoggingObservationHandler handler = new LoggingObservationHandler(config.getLogLevel());

        // 注册到 ObservationRegistry
        observationRegistry.observationConfig().observationHandler(handler);

        return handler;
    }

    /// 创建性能观测处理器。
    ///
    /// 功能：
    ///
    /// - 记录 Observation 执行时间
    /// - 检测慢操作并记录警告日志
    /// - 收集性能统计信息
    ///
    /// @param properties 可观测性配置属性
    /// @param observationRegistry Observation 注册中心
    /// @return PerformanceObservationHandler 实例
    @Bean
    @ConditionalOnProperty(
        prefix = "patra.observability.handlers.performance",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true  // 默认启用
    )
    public PerformanceObservationHandler performanceObservationHandler(
        ObservabilityProperties properties,
        ObservationRegistry observationRegistry
    ) {
        ObservabilityProperties.PerformanceHandlerConfig config = properties.getHandlers().getPerformance();

        PerformanceObservationHandler handler = new PerformanceObservationHandler(config.getSlowThreshold());

        // 注册到 ObservationRegistry
        observationRegistry.observationConfig().observationHandler(handler);

        return handler;
    }

    // 注意：不实施 MetricsObservationHandler
    // 理由：Spring Boot 已自动配置 DefaultMeterObservationHandler，自动将 Observation 转换为 Timer 指标
    // 重复实现会导致指标重复收集，如需自定义指标逻辑，应通过 MeterFilter 实现

    // ==================== MeterFilter（任务 2.6） ====================

    /// 创建高基数标签过滤器。
    ///
    /// 功能：
    ///
    /// - 过滤高基数标签（userId、requestId、traceId 等）
    /// - 防止时序数据库性能问题
    /// - 支持自定义高基数标签黑名单
    ///
    /// 注意：
    ///
    /// - HighCardinalityMeterFilter 应该最先执行，在其他 Filter 之前移除高基数标签
    /// - 生产环境必须启用，保护时序数据库
    ///
    /// @return HighCardinalityMeterFilter 实例
    @Bean
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
    /// @param properties 可观测性配置属性
    /// @return MetricNamingMeterFilter 实例
    @Bean
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
    /// 注意：
    ///
    /// - CommonTagsMeterFilter 应该最后执行，在标签和名称都规范后添加公共标签
    ///
    /// @param properties 可观测性配置属性
    /// @return CommonTagsMeterFilter 实例
    @Bean
    public CommonTagsMeterFilter commonTagsMeterFilter(ObservabilityProperties properties) {
        return new CommonTagsMeterFilter(
            properties.getApplicationName(),
            properties.getEnvironment(),
            properties.getRegion(),
            properties.getCluster(),
            properties.getMetrics().getCommonTags()
        );
    }
}
