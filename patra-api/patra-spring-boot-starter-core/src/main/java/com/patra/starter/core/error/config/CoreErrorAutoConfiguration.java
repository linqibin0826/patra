package com.patra.starter.core.error.config;

import com.patra.starter.core.error.circuit.CircuitBreaker;
import com.patra.starter.core.error.circuit.CircuitBreakerProtectedContributor;
import com.patra.starter.core.error.circuit.DefaultCircuitBreaker;
import com.patra.starter.core.error.metrics.DefaultErrorMetrics;
import com.patra.starter.core.error.metrics.ErrorMetrics;
import com.patra.starter.core.error.service.ErrorResolutionService;
import com.patra.starter.core.error.spi.ErrorMappingContributor;
import com.patra.starter.core.error.spi.StatusMappingStrategy;
import com.patra.starter.core.error.spi.TraceProvider;
import com.patra.starter.core.error.strategy.SuffixHeuristicStatusMappingStrategy;
import com.patra.starter.core.error.trace.HeaderBasedTraceProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 核心错误处理自动装配。
 *
 * <p>为各 SPI 提供条件化的默认实现，包括：状态映射、Trace 提供者、指标采集、
 * 熔断保护的映射贡献者，以及错误解析服务等。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Configuration
@EnableConfigurationProperties({ErrorProperties.class, TracingProperties.class})
@ConditionalOnProperty(prefix = "patra.error", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CoreErrorAutoConfiguration {
    
    /**
     * 默认的基于后缀启发式的状态映射策略（缺省实现）。
     *
     * @return 状态映射策略实例
     */
    @Bean
    @ConditionalOnMissingBean
    public StatusMappingStrategy defaultStatusMappingStrategy() {
        log.debug("Creating default SuffixHeuristicStatusMappingStrategy");
        return new SuffixHeuristicStatusMappingStrategy();
    }
    
    /**
     * 默认的基于请求头名从 MDC 提取 TraceId 的提供者（缺省实现）。
     *
     * @param tracingProperties 追踪配置
     * @return Trace 提供者实例
     */
    @Bean
    @ConditionalOnMissingBean
    public TraceProvider defaultTraceProvider(TracingProperties tracingProperties) {
        log.debug("Creating default HeaderBasedTraceProvider with headers: {}", 
                 tracingProperties.getHeaderNames());
        return new HeaderBasedTraceProvider(tracingProperties);
    }
    
    /**
     * 默认的错误指标实现（若用户未自定义则注入）。
     *
     * @return 指标实现
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "patra.error.monitoring.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ErrorMetrics defaultErrorMetrics() {
        log.debug("Creating default ErrorMetrics implementation");
        return new DefaultErrorMetrics();
    }
    
    /**
     * 带熔断保护的错误映射贡献者集合（按需包装）。
     *
     * @param errorProperties 错误处理配置
     * @param originalContributors 原始贡献者集合
     * @return 包装后的贡献者集合
     */
    @Bean
    @ConditionalOnProperty(prefix = "patra.error.monitoring.circuit-breaker", name = "enabled", havingValue = "true", matchIfMissing = true)
    public List<ErrorMappingContributor> circuitBreakerProtectedContributors(
            ErrorProperties errorProperties,
            List<ErrorMappingContributor> originalContributors) {
        
        ErrorProperties.CircuitBreakerProperties cbProps = errorProperties.getMonitoring().getCircuitBreaker();
        
        log.info("Creating circuit breaker protected contributors: {} contributors", originalContributors.size());
        
        return originalContributors.stream()
            .map(contributor -> {
                String contributorName = contributor.getClass().getSimpleName();
                CircuitBreaker circuitBreaker = new DefaultCircuitBreaker(
                    contributorName,
                    cbProps.getFailureThreshold(),
                    cbProps.getFailureRateThreshold(),
                    Duration.ofMillis(cbProps.getTimeoutMs()),
                    cbProps.getSlidingWindowSize()
                );
                
                log.debug("Created circuit breaker for contributor: {}", contributorName);
                return new CircuitBreakerProtectedContributor(contributor, circuitBreaker);
            })
            .collect(Collectors.toList());
    }
    
    /**
     * 错误解析服务，负责编排错误解析算法。
     *
     * @param errorProperties 错误处理配置
     * @param statusMappingStrategy 状态映射策略
     * @param mappingContributors 错误映射贡献者集合（可能包含熔断包装）
     * @param errorMetrics 指标采集器
     * @return 错误解析服务实例
     */
    @Bean
    public ErrorResolutionService errorResolutionService(
            ErrorProperties errorProperties,
            StatusMappingStrategy statusMappingStrategy,
            List<ErrorMappingContributor> mappingContributors,
            ErrorMetrics errorMetrics) {
        
        log.info("Creating ErrorResolutionService with context prefix: '{}', {} mapping contributors", 
                errorProperties.getContextPrefix(), mappingContributors.size());
        
        if (errorProperties.getContextPrefix() == null || errorProperties.getContextPrefix().trim().isEmpty()) {
            log.warn("Context prefix is not configured! Error codes will use 'UNKNOWN' prefix. " +
                    "Please set patra.error.context-prefix property.");
        }
        
        return new ErrorResolutionService(errorProperties, statusMappingStrategy, mappingContributors, errorMetrics);
    }
}
