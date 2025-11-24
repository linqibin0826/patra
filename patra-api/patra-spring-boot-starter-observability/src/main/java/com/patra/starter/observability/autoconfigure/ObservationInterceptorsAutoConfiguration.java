package com.patra.starter.observability.autoconfigure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 可观测性拦截器自动配置。
 *
 * <p>根据类路径中是否存在对应的 Starter，自动注册相应的拦截器：
 * <ul>
 *   <li>{@code ObservationResolutionInterceptor} - 错误处理管道观测拦截器（patra-starter-core）</li>
 *   <li>{@code RestClientObservationInterceptor} - HTTP 客户端观测拦截器（patra-starter-rest-client）</li>
 *   <li>{@code BatchObservationJobListener} - Batch 任务观测监听器（patra-starter-batch）</li>
 * </ul>
 *
 * <p>拦截器注册策略：
 * <ul>
 *   <li>使用 {@code @ConditionalOnClass} 检测对应 Starter 的扩展点接口是否存在</li>
 *   <li>如果存在，自动注册对应的观测拦截器</li>
 *   <li>插件式架构：observability → core（单向依赖，符合 DIP）</li>
 * </ul>
 *
 * <p>注意：
 * <ul>
 *   <li>拦截器实现类将在任务 2.7 中实现</li>
 *   <li>扩展点接口定义在各自的 Starter 中（ResolutionInterceptor、ClientInterceptor 等）</li>
 * </ul>
 *
 * @author Jobs
 * @since 1.0.0
 * @see com.patra.starter.core.error.pipeline.ResolutionInterceptor
 */
@AutoConfiguration(after = ObservabilityAutoConfiguration.class)
@ConditionalOnProperty(
    prefix = "patra.observability",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class ObservationInterceptorsAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ObservationInterceptorsAutoConfiguration.class);

    /**
     * 构造函数。
     */
    public ObservationInterceptorsAutoConfiguration() {
        log.info("初始化可观测性拦截器自动配置");
    }

    // TODO: 任务 2.7 将在此处注册拦截器 Bean
    // - ObservationResolutionInterceptor（实现 ResolutionInterceptor）
    // - RestClientObservationInterceptor（实现 ClientInterceptor）
    // - BatchObservationJobListener（实现 JobExecutionListener）
    //
    // 示例代码（任务 2.7 实现）：
    // @Bean
    // @ConditionalOnClass(name = "com.patra.starter.core.error.pipeline.ResolutionInterceptor")
    // public ObservationResolutionInterceptor observationResolutionInterceptor(
    //     ObservationRegistry observationRegistry
    // ) {
    //     return new ObservationResolutionInterceptor(observationRegistry);
    // }
}
