package com.patra.starter.core.error.config;

import com.patra.common.error.codes.HttpStdErrors;
import com.patra.starter.core.error.engine.DefaultErrorResolutionEngine;
import com.patra.starter.core.error.engine.ErrorResolutionEngine;
import com.patra.starter.core.error.observation.ErrorObservationRecorder;
import com.patra.starter.core.error.observation.MicrometerErrorObservationRecorder;
import com.patra.starter.core.error.pipeline.ErrorResolutionPipeline;
import com.patra.starter.core.error.pipeline.ResolutionInterceptor;
import com.patra.starter.core.error.pipeline.interceptor.CircuitBreakerInterceptor;
import com.patra.starter.core.error.pipeline.interceptor.MetricsInterceptor;
import com.patra.starter.core.error.pipeline.interceptor.TracingInterceptor;
import com.patra.starter.core.error.spi.ErrorMappingContributor;
import com.patra.starter.core.error.spi.TraceProvider;
import com.patra.starter.core.error.trace.HeaderBasedTraceProvider;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Auto-configuration for platform error handling.
 * <p>Provides default engine, interceptors, and observation beans which can be overridden by applications.</p>
 */
@Slf4j
@Configuration
@EnableConfigurationProperties({ErrorProperties.class, TracingProperties.class})
@ConditionalOnProperty(prefix = "patra.error", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CoreErrorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TraceProvider defaultTraceProvider(TracingProperties tracingProperties) {
        log.debug("Using default header-based TraceProvider: {}", tracingProperties.getHeaderNames());
        return new HeaderBasedTraceProvider(tracingProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public ErrorObservationRecorder errorObservationRecorder(ErrorProperties errorProperties,
                                                             ObjectProvider<MeterRegistry> meterRegistryProvider) {
        if (!errorProperties.getObservation().isEnabled()) {
            log.info("Error observation disabled; injecting NO_OP recorder");
            return ErrorObservationRecorder.NO_OP;
        }
        MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
        if (meterRegistry == null) {
            log.warn("Micrometer MeterRegistry not available; degrading observation to NO_OP");
            return ErrorObservationRecorder.NO_OP;
        }
        return new MicrometerErrorObservationRecorder(meterRegistry, errorProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public ErrorResolutionEngine errorResolutionEngine(ErrorProperties errorProperties,
                                                       List<ErrorMappingContributor> mappingContributors) {
        if (errorProperties.getContextPrefix() == null || errorProperties.getContextPrefix().isBlank()) {
            log.warn("patra.error.context-prefix is not configured; using UNKNOWN as the unified error prefix");
        }
        return new DefaultErrorResolutionEngine(errorProperties, mappingContributors);
    }

    @Bean
    public ErrorResolutionPipeline errorResolutionPipeline(ErrorResolutionEngine engine,
                                                           ObjectProvider<ResolutionInterceptor> interceptorsProvider) {
        List<ResolutionInterceptor> interceptors = interceptorsProvider.orderedStream().toList();
        log.info("Building error-resolution pipeline with {} interceptors", interceptors.size());
        return new ErrorResolutionPipeline(engine, interceptors);
    }

    @Bean
    @ConditionalOnProperty(prefix = "patra.error.observation", name = "enabled", havingValue = "true", matchIfMissing = true)
    public MetricsInterceptor metricsInterceptor(ErrorObservationRecorder observationRecorder,
                                                 ErrorProperties errorProperties) {
        return new MetricsInterceptor(observationRecorder, errorProperties.getObservation());
    }

    @Bean
    @ConditionalOnMissingBean
    public TracingInterceptor tracingInterceptor(TraceProvider traceProvider) {
        return new TracingInterceptor(traceProvider);
    }

    @Bean(name = "errorResolutionCircuitBreaker")
    @ConditionalOnProperty(prefix = "patra.error.circuit-breaker", name = "enabled", havingValue = "true", matchIfMissing = true)
    public CircuitBreaker errorResolutionCircuitBreaker(ErrorProperties errorProperties) {
        ErrorProperties.CircuitBreakerProperties cb = errorProperties.getCircuitBreaker();
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(cb.getFailureRateThreshold())
                .minimumNumberOfCalls(cb.getMinimumNumberOfCalls())
                .slidingWindowSize(cb.getSlidingWindowSize())
                .permittedNumberOfCallsInHalfOpenState(cb.getPermittedCallsInHalfOpenState())
                .waitDurationInOpenState(cb.getWaitDurationInOpenState())
                .build();
        log.info("Creating error-resolution circuit breaker: failureRate={} slidingWindow={}",
                cb.getFailureRateThreshold(), cb.getSlidingWindowSize());
        return CircuitBreaker.of("patra-error-resolution", config);
    }

    @Bean
    @ConditionalOnBean(name = "errorResolutionCircuitBreaker")
    public CircuitBreakerInterceptor circuitBreakerInterceptor(
            @Qualifier("errorResolutionCircuitBreaker") CircuitBreaker circuitBreaker,
            ErrorObservationRecorder observationRecorder,
            ErrorProperties errorProperties) {
        return new CircuitBreakerInterceptor(circuitBreaker, observationRecorder, errorProperties);
    }

    @Bean
    @ConditionalOnMissingBean(HttpStdErrors.Group.class)
    public HttpStdErrors.Group httpStdErrorsGroup(ErrorProperties errorProperties) {
        String prefix = errorProperties.getContextPrefix();
        if (prefix == null || prefix.isBlank()) {
            prefix = "UNKNOWN";
        }
        return HttpStdErrors.of(prefix);
    }
}
