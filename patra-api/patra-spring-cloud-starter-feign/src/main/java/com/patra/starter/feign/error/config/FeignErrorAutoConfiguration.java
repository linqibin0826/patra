package com.patra.starter.feign.error.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.starter.core.error.config.TracingProperties;
import com.patra.starter.core.error.spi.TraceProvider;
import com.patra.starter.feign.error.decoder.ProblemDetailErrorDecoder;
import com.patra.starter.feign.error.interceptor.TraceIdRequestInterceptor;
import com.patra.starter.feign.error.metrics.DefaultFeignErrorMetrics;
import com.patra.starter.feign.error.metrics.FeignErrorMetrics;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Feign 错误处理自动装配。
 *
 * <p>自动注册以下组件：
 * - 错误解码器 {@link com.patra.starter.feign.error.decoder.ProblemDetailErrorDecoder}
 * - 请求拦截器 {@link com.patra.starter.feign.error.interceptor.TraceIdRequestInterceptor}
 * - 指标采集 {@link com.patra.starter.feign.error.metrics.FeignErrorMetrics}
 *
 * <p>生效条件：
 * - 类路径存在 Feign 相关类
 * - 配置项 {@code patra.feign.problem.enabled=true}（默认开启）
 * - 存在必要依赖（如 {@link com.fasterxml.jackson.databind.ObjectMapper}、{@link com.patra.starter.core.error.spi.TraceProvider}）
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties({FeignErrorProperties.class})
@ConditionalOnClass(name = {
    "feign.Feign",
    "feign.codec.ErrorDecoder",
    "org.springframework.http.ProblemDetail"
})
@ConditionalOnProperty(
    prefix = "patra.feign.problem", 
    name = "enabled", 
    havingValue = "true", 
    matchIfMissing = true
)
public class FeignErrorAutoConfiguration {
    
    /**
     * 默认的 Feign 错误指标实现（若用户未自定义则注入本实现）。
     *
     * @return 默认指标实现
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "patra.feign.problem.monitoring", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FeignErrorMetrics defaultFeignErrorMetrics() {
        log.debug("Creating default FeignErrorMetrics implementation");
        return new DefaultFeignErrorMetrics();
    }
    
    /**
     * 配置基于 {@link org.springframework.http.ProblemDetail} 的错误解码器。
     *
     * @param objectMapper Jackson 解析器
     * @param properties 错误处理配置
     * @param feignErrorMetrics 指标采集器
     * @return 错误解码器实例
     */
    @Bean
    @ConditionalOnMissingBean(ErrorDecoder.class)
    public ErrorDecoder problemDetailErrorDecoder(ObjectMapper objectMapper, 
                                                 FeignErrorProperties properties,
                                                 FeignErrorMetrics feignErrorMetrics) {
        log.info("Configuring ProblemDetailErrorDecoder, tolerant={}", properties.isTolerant());
        return new ProblemDetailErrorDecoder(objectMapper, properties, feignErrorMetrics);
    }
    
    /**
     * 配置 TraceId 透传拦截器，保持跨服务的链路追踪关联。
     *
     * @param traceProvider TraceId 提供者
     * @param tracingProperties 链路追踪配置
     * @return 请求拦截器实例
     */
    @Bean
    @ConditionalOnMissingBean(TraceIdRequestInterceptor.class)
    public TraceIdRequestInterceptor traceIdRequestInterceptor(TraceProvider traceProvider,
                                                              TracingProperties tracingProperties) {
        log.info("Configuring TraceIdRequestInterceptor, headers={}", tracingProperties.getHeaderNames());
        return new TraceIdRequestInterceptor(traceProvider, tracingProperties);
    }
}
