package com.patra.starter.core.error.config;

import com.patra.starter.core.error.observation.ErrorObservationRecorder;
import com.patra.starter.core.error.pipeline.interceptor.CircuitBreakerInterceptor;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for Circuit Breaker support in error handling.
 *
 * <p>Only activated when Resilience4j is on the classpath and explicitly enabled via configuration.
 *
 * <p>This configuration is separated from {@link CoreErrorAutoConfiguration} to avoid
 * ClassNotFoundException when Resilience4j is not present (optional dependency).
 */
@Slf4j
@AutoConfiguration(after = CoreErrorAutoConfiguration.class)
@ConditionalOnClass(CircuitBreaker.class)
@EnableConfigurationProperties(ErrorProperties.class)
public class CircuitBreakerErrorAutoConfiguration {

  @Bean(name = "errorResolutionCircuitBreaker")
  @ConditionalOnProperty(
      prefix = "patra.error.circuit-breaker",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public CircuitBreaker errorResolutionCircuitBreaker(ErrorProperties errorProperties) {
    ErrorProperties.CircuitBreakerProperties cb = errorProperties.getCircuitBreaker();
    CircuitBreakerConfig config =
        CircuitBreakerConfig.custom()
            .failureRateThreshold(cb.getFailureRateThreshold())
            .minimumNumberOfCalls(cb.getMinimumNumberOfCalls())
            .slidingWindowSize(cb.getSlidingWindowSize())
            .permittedNumberOfCallsInHalfOpenState(cb.getPermittedCallsInHalfOpenState())
            .waitDurationInOpenState(cb.getWaitDurationInOpenState())
            .build();
    log.info(
        "Creating error-resolution circuit breaker: failureRate={} slidingWindow={}",
        cb.getFailureRateThreshold(),
        cb.getSlidingWindowSize());
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
}
