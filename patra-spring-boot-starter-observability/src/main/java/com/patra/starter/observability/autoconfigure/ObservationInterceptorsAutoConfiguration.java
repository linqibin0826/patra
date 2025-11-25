package com.patra.starter.observability.autoconfigure;

import com.patra.starter.observability.interceptor.BatchObservationJobListener;
import com.patra.starter.observability.interceptor.ObservationResolutionInterceptor;
import com.patra.starter.observability.interceptor.RestClientObservationInterceptor;
import com.patra.starter.observability.interceptor.redisson.LockMetricsRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/// 可观测性拦截器自动配置。
///
/// 根据类路径中是否存在对应的 Starter，自动注册相应的拦截器：
///
/// - ObservationResolutionInterceptor - 错误处理管道观测拦截器（patra-starter-core）
/// - RestClientObservationInterceptor - HTTP 客户端观测拦截器（patra-starter-rest-client）
/// - BatchObservationJobListener - Batch 任务观测监听器（patra-starter-batch）
/// - LockMetricsRecorder - 分布式锁指标记录器（patra-starter-redisson）
///
/// 拦截器注册策略：
///
/// - 使用 @ConditionalOnClass 检测对应 Starter 的核心类是否存在
/// - 如果存在，自动注册对应的观测拦截器
/// - 插件式架构：observability → core（单向依赖）
///
/// 设计说明：
///
/// - ObservationResolutionInterceptor 实现自定义的 ResolutionInterceptor 扩展点（符合 DIP）
/// - RestClientObservationInterceptor 实现 Spring 标准的 ClientHttpRequestInterceptor（生命周期管理更可靠）
/// - BatchObservationJobListener 实现 Spring Batch 标准的 JobExecutionListener
///
/// @author Jobs
/// @since 1.0.0
/// @see com.patra.starter.core.error.pipeline.ResolutionInterceptor
/// @see org.springframework.http.client.ClientHttpRequestInterceptor
/// @see org.springframework.batch.core.JobExecutionListener
@AutoConfiguration(after = ObservabilityAutoConfiguration.class)
@ConditionalOnProperty(
    prefix = "patra.observability",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class ObservationInterceptorsAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ObservationInterceptorsAutoConfiguration.class);

    /// 构造函数。
    public ObservationInterceptorsAutoConfiguration() {
        log.info("初始化可观测性拦截器自动配置");
    }

    /// 注册错误解析可观测性拦截器。
    ///
    /// 仅在 patra-spring-boot-starter-core 存在时生效。
    ///
    /// @param observationRegistry Micrometer Observation 注册表
    /// @return 错误解析拦截器实例
    @Bean
    @ConditionalOnClass(name = "com.patra.starter.core.error.pipeline.ResolutionInterceptor")
    public ObservationResolutionInterceptor observationResolutionInterceptor(
        ObservationRegistry observationRegistry
    ) {
        log.debug("注册错误解析可观测性拦截器");
        return new ObservationResolutionInterceptor(observationRegistry);
    }

    /// 注册 REST 客户端可观测性拦截器。
    ///
    /// 仅在 patra-spring-boot-starter-rest-client 存在时生效。
    ///
    /// 注意：虽然我们最初设计了 ClientInterceptor 扩展点，但此拦截器实现了 Spring 标准的
    /// ClientHttpRequestInterceptor 接口，以便更可靠地管理 Micrometer Observation 生命周期。
    /// 使用 try-finally 确保 Observation 一定会停止，避免资源泄漏。
    ///
    /// @param observationRegistry Micrometer Observation 注册表
    /// @return REST 客户端拦截器实例
    @Bean
    @ConditionalOnClass(name = "org.springframework.web.client.RestClient")
    public RestClientObservationInterceptor restClientObservationInterceptor(
        ObservationRegistry observationRegistry
    ) {
        log.debug("注册 REST 客户端可观测性拦截器");
        return new RestClientObservationInterceptor(observationRegistry);
    }

    /// 注册批处理任务可观测性监听器。
    ///
    /// 仅在 patra-spring-boot-starter-batch 存在时生效。
    ///
    /// @param observationRegistry Micrometer Observation 注册表
    /// @return 批处理任务监听器实例
    @Bean
    @ConditionalOnClass(name = "org.springframework.batch.core.JobExecutionListener")
    public BatchObservationJobListener batchObservationJobListener(
        ObservationRegistry observationRegistry
    ) {
        log.debug("注册批处理任务可观测性监听器");
        return new BatchObservationJobListener(observationRegistry);
    }

    /// 注册分布式锁指标记录器。
    ///
    /// 仅在 patra-spring-boot-starter-redisson 存在时生效。
    ///
    /// **SPI 设计说明**：
    /// - `LockObserver` 接口定义在 redisson 模块
    /// - `LockMetricsRecorder` 实现该接口，提供 Micrometer 指标记录
    /// - 通过依赖倒置，redisson 不需要编译期依赖 observability
    ///
    /// @param meterRegistry Micrometer 指标注册表
    /// @return 分布式锁指标记录器实例（作为 LockObserver 注入到 LockExecutor）
    @Bean
    @ConditionalOnClass(name = "org.redisson.api.RedissonClient")
    @ConditionalOnBean(MeterRegistry.class)
    public LockMetricsRecorder lockMetricsRecorder(MeterRegistry meterRegistry) {
        log.debug("注册分布式锁指标记录器");
        return new LockMetricsRecorder(meterRegistry);
    }
}
