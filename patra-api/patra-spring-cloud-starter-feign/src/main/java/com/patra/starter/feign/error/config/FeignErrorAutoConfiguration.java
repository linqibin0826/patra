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
 * Auto-configuration for Feign error handling components.
 * Provides automatic registration of error decoders, request interceptors,
 * and other Feign-specific error handling infrastructure.
 * 
 * This configuration is activated when:
 * - Feign classes are present on the classpath
 * - The patra.feign.problem.enabled property is true (default)
 * - Required dependencies (ObjectMapper, TraceProvider) are available
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
     * Default Feign error metrics implementation for collecting Feign error handling statistics.
     * Only created if no custom implementation is provided.
     * 
     * @return default Feign error metrics implementation
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "patra.feign.problem.monitoring", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FeignErrorMetrics defaultFeignErrorMetrics() {
        log.debug("Creating default FeignErrorMetrics implementation");
        return new DefaultFeignErrorMetrics();
    }
    
    /**
     * Configures the ProblemDetail error decoder for Feign clients.
     * This decoder automatically converts RFC 7807 ProblemDetail responses
     * from downstream services into RemoteCallException instances.
     * 
     * @param objectMapper the Jackson ObjectMapper for JSON parsing
     * @param properties the Feign error handling configuration
     * @param feignErrorMetrics the metrics collector for Feign error handling
     * @return the configured error decoder
     */
    @Bean
    @ConditionalOnMissingBean(ErrorDecoder.class)
    public ErrorDecoder problemDetailErrorDecoder(ObjectMapper objectMapper, 
                                                 FeignErrorProperties properties,
                                                 FeignErrorMetrics feignErrorMetrics) {
        log.info("Configuring ProblemDetailErrorDecoder with tolerant mode: {}", 
                properties.isTolerant());
        return new ProblemDetailErrorDecoder(objectMapper, properties, feignErrorMetrics);
    }
    
    /**
     * Configures the trace ID request interceptor for automatic trace propagation.
     * This interceptor adds trace IDs to outgoing Feign requests to maintain
     * distributed tracing correlation across service boundaries.
     * 
     * @param traceProvider the trace provider for extracting current trace ID
     * @param tracingProperties the tracing configuration
     * @return the configured request interceptor
     */
    @Bean
    @ConditionalOnMissingBean(TraceIdRequestInterceptor.class)
    public TraceIdRequestInterceptor traceIdRequestInterceptor(TraceProvider traceProvider,
                                                              TracingProperties tracingProperties) {
        log.info("Configuring TraceIdRequestInterceptor with headers: {}", 
                tracingProperties.getHeaderNames());
        return new TraceIdRequestInterceptor(traceProvider, tracingProperties);
    }
}