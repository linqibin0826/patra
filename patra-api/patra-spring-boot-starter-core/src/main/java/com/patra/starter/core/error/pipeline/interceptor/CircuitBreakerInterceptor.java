package com.patra.starter.core.error.pipeline.interceptor;

import com.patra.starter.core.error.config.ErrorProperties;
import com.patra.starter.core.error.model.ErrorResolution;
import com.patra.starter.core.error.observation.ErrorObservationRecorder;
import com.patra.starter.core.error.pipeline.ResolutionInterceptor;
import com.patra.starter.core.error.pipeline.ResolutionInvocation;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * 熔断拦截器：使用 Resilience4j 对解析过程进行保护。
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CircuitBreakerInterceptor implements ResolutionInterceptor {

    private final CircuitBreaker circuitBreaker;
    private final ErrorObservationRecorder observationRecorder;
    private final String contextPrefix;

    public CircuitBreakerInterceptor(CircuitBreaker circuitBreaker,
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
            log.warn("错误解析熔断器开启，使用兜底错误码。原因: {}", ex.getMessage());
            return new ErrorResolution(new com.patra.common.error.codes.ErrorCodeLike() {
                @Override public String code() { return contextPrefix + "-0503"; }
                @Override public int httpStatus() { return 503; }
                @Override public String toString() { return code(); }
            }, 503);
        }
    }
}
