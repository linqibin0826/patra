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
 * Auto-configuration for core error handling infrastructure.
 * Provides default implementations for all SPI interfaces with conditional beans.
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
     * Default status mapping strategy using suffix heuristics.
     * Only created if no custom implementation is provided.
     * 
     * @return suffix heuristic status mapping strategy
     */
    @Bean
    @ConditionalOnMissingBean
    public StatusMappingStrategy defaultStatusMappingStrategy() {
        log.debug("Creating default SuffixHeuristicStatusMappingStrategy");
        return new SuffixHeuristicStatusMappingStrategy();
    }
    
    /**
     * Default trace provider using header-based extraction from MDC.
     * Only created if no custom implementation is provided.
     * 
     * @param tracingProperties tracing configuration properties
     * @return header-based trace provider
     */
    @Bean
    @ConditionalOnMissingBean
    public TraceProvider defaultTraceProvider(TracingProperties tracingProperties) {
        log.debug("Creating default HeaderBasedTraceProvider with headers: {}", 
                 tracingProperties.getHeaderNames());
        return new HeaderBasedTraceProvider(tracingProperties);
    }
    
    /**
     * Default error metrics implementation for collecting error handling statistics.
     * Only created if no custom implementation is provided.
     * 
     * @return default error metrics implementation
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "patra.error.monitoring.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ErrorMetrics defaultErrorMetrics() {
        log.debug("Creating default ErrorMetrics implementation");
        return new DefaultErrorMetrics();
    }
    
    /**
     * Circuit breaker-protected error mapping contributors.
     * Wraps each contributor with circuit breaker protection when enabled.
     * 
     * @param errorProperties error configuration properties
     * @param originalContributors list of original ErrorMappingContributor beans
     * @return list of circuit breaker protected contributors
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
     * Error resolution service that orchestrates the error resolution algorithm.
     * Uses all available ErrorMappingContributor beans for fine-grained mappings.
     * 
     * @param errorProperties error configuration properties
     * @param statusMappingStrategy status mapping strategy
     * @param mappingContributors list of all ErrorMappingContributor beans (potentially circuit breaker protected)
     * @param errorMetrics error metrics collector
     * @return error resolution service
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