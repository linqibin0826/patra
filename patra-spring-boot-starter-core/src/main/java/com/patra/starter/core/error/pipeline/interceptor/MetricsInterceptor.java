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
 * 指标拦截器：记录解析耗时、慢调用与总量指标。
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
            log.warn("错误解析耗时较长: {} ms, 异常={}, 错误码={}", durationMs,
                    exception == null ? "Null" : exception.getClass().getSimpleName(),
                    resolution.errorCode().code());
        }
        return resolution;
    }
}
