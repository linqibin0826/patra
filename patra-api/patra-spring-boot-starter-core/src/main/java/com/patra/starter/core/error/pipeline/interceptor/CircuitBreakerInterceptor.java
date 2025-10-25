package com.patra.starter.core.error.pipeline.interceptor;

import com.patra.common.error.codes.ErrorCodeLike;
import com.patra.starter.core.error.config.ErrorProperties;
import com.patra.starter.core.error.model.ErrorResolution;
import com.patra.starter.core.error.model.SimpleErrorCode;
import com.patra.starter.core.error.observation.ErrorObservationRecorder;
import com.patra.starter.core.error.pipeline.ResolutionInterceptor;
import com.patra.starter.core.error.pipeline.ResolutionInvocation;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * Interceptor that safeguards the error-resolution pipeline with a Resilience4j circuit breaker.
 * When the breaker is open a synthetic 503 error code is returned to prevent cascading failures.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CircuitBreakerInterceptor implements ResolutionInterceptor {

  private final CircuitBreaker circuitBreaker;
  private final ErrorObservationRecorder observationRecorder;
  private final String contextPrefix;

  public CircuitBreakerInterceptor(
      CircuitBreaker circuitBreaker,
      ErrorObservationRecorder observationRecorder,
      ErrorProperties errorProperties) {
    this.circuitBreaker = circuitBreaker;
    this.observationRecorder = observationRecorder;
    String prefix = errorProperties.getContextPrefix();
    this.contextPrefix = (prefix == null || prefix.isBlank()) ? "UNKNOWN" : prefix;
  }

  @Override
  public ErrorResolution intercept(Throwable exception, ResolutionInvocation invocation) {
    try {
      return circuitBreaker.executeSupplier(() -> invocation.proceed(exception));
    } catch (CallNotPermittedException ex) {
      observationRecorder.recordCircuitBreakerFallback(exception);
      log.warn(
          "Circuit breaker opened during error resolution; using fallback error code. reason={}",
          ex.getMessage());
      ErrorCodeLike code = SimpleErrorCode.create(contextPrefix, "0503");
      return new ErrorResolution(code, code.httpStatus());
    }
  }
}
