package com.patra.starter.core.error.pipeline.interceptor;

import com.patra.starter.core.error.config.ErrorProperties;
import com.patra.starter.core.error.model.ErrorResolution;
import com.patra.starter.core.error.observation.ErrorObservationRecorder;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class CircuitBreakerInterceptorTest {

    @Test
    void closed_state_should_delegate_open_state_should_fallback() {
        CircuitBreaker cb = CircuitBreaker.of("test", CircuitBreakerConfig.custom()
                .failureRateThreshold(50f)
                .minimumNumberOfCalls(1)
                .slidingWindowSize(2)
                .waitDurationInOpenState(Duration.ofMillis(100))
                .build());

        ErrorObservationRecorder recorder = new ErrorObservationRecorder() {
            @Override public void recordResolution(Throwable exception, ErrorResolution resolution, long durationMs, boolean slow) { }
            @Override public void recordCircuitBreakerFallback(Throwable exception) { }
        };
        ErrorProperties props = new ErrorProperties();
        props.setContextPrefix("ING");
        CircuitBreakerInterceptor interceptor = new CircuitBreakerInterceptor(cb, recorder, props);

        // Closed state: delegate to downstream supplier
        ErrorResolution ok = interceptor.intercept(new RuntimeException(), ex -> new ErrorResolution(new com.patra.common.error.codes.ErrorCodeLike() {
            @Override public String code() { return "ING-0404"; }
            @Override public int httpStatus() { return 404; }
        }, 404));
        assertThat(ok.httpStatus()).isEqualTo(404);

        // Open state: return fallback 503 response
        cb.transitionToOpenState();
        ErrorResolution fb = interceptor.intercept(new RuntimeException(), ex -> new ErrorResolution(new com.patra.common.error.codes.ErrorCodeLike() {
            @Override public String code() { return "ING-0404"; }
            @Override public int httpStatus() { return 404; }
        }, 404));
        assertThat(fb.httpStatus()).isEqualTo(503);
        assertThat(fb.errorCode().code()).isEqualTo("ING-0503");
    }
}
