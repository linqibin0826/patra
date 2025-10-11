package com.patra.starter.core.error.pipeline.interceptor;

import com.patra.starter.core.error.config.ErrorProperties;
import com.patra.starter.core.error.model.ErrorResolution;
import com.patra.starter.core.error.observation.ErrorObservationRecorder;
import com.patra.starter.core.error.pipeline.ResolutionInterceptor;
import com.patra.starter.core.error.pipeline.ResolutionInvocation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.util.concurrent.TimeUnit;

/**
 * Interceptor that records timing information, slow invocations, and aggregate metrics for the
 * error-resolution pipeline.
 */
@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class MetricsInterceptor implements ResolutionInterceptor {

    private final ErrorObservationRecorder observationRecorder;
    private final ErrorProperties.ObservationProperties observationProperties;

    public MetricsInterceptor(ErrorObservationRecorder observationRecorder,
                              ErrorProperties.ObservationProperties observationProperties) {
        this.observationRecorder = observationRecorder;
        this.observationProperties = observationProperties;
    }

    @Override
    public ErrorResolution intercept(Throwable exception, ResolutionInvocation invocation) {
        long start = System.nanoTime();
        ErrorResolution resolution = invocation.proceed(exception);
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        boolean slow = durationMs >= observationProperties.getSlowThresholdMs();

        observationRecorder.recordResolution(exception, resolution, durationMs, slow);

        if (slow && observationProperties.isLogSlowResolution()) {
            log.warn("Slow error resolution detected: {} ms, exception={}, errorCode={}", durationMs,
                    exception == null ? "Null" : exception.getClass().getSimpleName(),
                    resolution.errorCode().code());
        }
        return resolution;
    }
}
