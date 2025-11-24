package com.patra.starter.observability.autoconfigure;

import com.patra.starter.observability.config.ObservabilityProperties;
import com.patra.starter.observability.interceptor.BatchObservationJobListener;
import com.patra.starter.observability.interceptor.ObservationResolutionInterceptor;
import com.patra.starter.observability.interceptor.RestClientObservationInterceptor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.ClientHttpRequestInterceptor;

/**
 * Patra 可观测性自动配置。
 *
 * <p>提供统一的可观测性基础设施，包括：
 * <ul>
 *   <li>ObservationRegistry 配置和 Handler 注册</li>
 *   <li>公共标签配置</li>
 *   <li>@Observed 注解支持</li>
 *   <li>命名规范配置</li>
 * </ul>
 *
 * @author Jobs
 * @since 1.0.0
 */
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

    /**
     * 构造函数。
     *
     * @param properties 可观测性配置属性
     */
    public ObservabilityAutoConfiguration(ObservabilityProperties properties) {
        log.info("初始化 Patra 可观测性自动配置 [环境: {}, 应用: {}]",
            properties.getEnvironment(),
            properties.getApplicationName() != null ? properties.getApplicationName() : "未配置");
    }

    /**
     * 创建或注入 ObservationRegistry。
     *
     * <p>ObservationRegistry 是 Micrometer Observation API 的核心，
     * 用于创建和管理 Observation 实例。
     *
     * @return ObservationRegistry 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public ObservationRegistry observationRegistry() {
        log.info("创建 ObservationRegistry");
        return ObservationRegistry.create();
    }

    /**
     * 启用 @Observed 注解支持。
     *
     * <p>通过 AOP 拦截标注 @Observed 的方法，自动创建 Observation。
     *
     * @param observationRegistry ObservationRegistry 实例
     * @return ObservedAspect 实例
     */
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

    // ==================== 拦截器（任务 2.7） ====================

    /**
     * 创建错误解析可观测性拦截器。
     *
     * <p>功能：
     * <ul>
     *   <li>为错误解析流程创建 Observation</li>
     *   <li>自动记录错误类型、错误类、解析结果等关键信息</li>
     *   <li>与其他可观测性组件（日志、指标、追踪）集成</li>
     * </ul>
     *
     * <p>注意：
     * <ul>
     *   <li>实现 ResolutionInterceptor 扩展点（来自 patra-starter-core）</li>
     *   <li>使用最高优先级确保最早执行，捕获整个解析流程</li>
     * </ul>
     *
     * @param observationRegistry Observation 注册中心
     * @return ObservationResolutionInterceptor 实例
     */
    @Bean
    public ObservationResolutionInterceptor observationResolutionInterceptor(
        ObservationRegistry observationRegistry
    ) {
        return new ObservationResolutionInterceptor(observationRegistry);
    }

    /**
     * 创建 REST 客户端可观测性拦截器。
     *
     * <p>功能：
     * <ul>
     *   <li>为 HTTP 请求创建 Observation</li>
     *   <li>自动记录请求方法、URI、状态码等关键信息</li>
     *   <li>与 Micrometer Observation 集成，自动生成追踪和指标</li>
     * </ul>
     *
     * <p>注意：
     * <ul>
     *   <li>仅在 Spring Web 存在时启用（spring-web 是可选依赖）</li>
     *   <li>实现 ClientHttpRequestInterceptor 接口</li>
     *   <li>需要在 RestTemplate 中手动注册此拦截器</li>
     * </ul>
     *
     * @param observationRegistry Observation 注册中心
     * @return RestClientObservationInterceptor 实例
     */
    @Bean
    @ConditionalOnClass(ClientHttpRequestInterceptor.class)
    public RestClientObservationInterceptor restClientObservationInterceptor(
        ObservationRegistry observationRegistry
    ) {
        return new RestClientObservationInterceptor(observationRegistry);
    }

    /**
     * 创建批处理任务可观测性监听器。
     *
     * <p>功能：
     * <ul>
     *   <li>为批处理任务创建 Observation</li>
     *   <li>自动记录任务名称、执行 ID、状态等关键信息</li>
     *   <li>与 Micrometer Observation 集成，自动生成追踪和指标</li>
     * </ul>
     *
     * <p>注意：
     * <ul>
     *   <li>仅在 Spring Batch 存在时启用（spring-batch-core 是可选依赖）</li>
     *   <li>实现 JobExecutionListener 接口</li>
     *   <li>需要在 Job 配置中手动注册此监听器</li>
     * </ul>
     *
     * @param observationRegistry Observation 注册中心
     * @return BatchObservationJobListener 实例
     */
    @Bean
    @ConditionalOnClass(JobExecutionListener.class)
    public BatchObservationJobListener batchObservationJobListener(
        ObservationRegistry observationRegistry
    ) {
        return new BatchObservationJobListener(observationRegistry);
    }
}
