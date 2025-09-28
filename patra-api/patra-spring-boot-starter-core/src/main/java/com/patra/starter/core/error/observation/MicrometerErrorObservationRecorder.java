package com.patra.starter.core.error.observation;

import com.patra.starter.core.error.config.ErrorProperties;
import com.patra.starter.core.error.model.ErrorResolution;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.TimeUnit;

/**
 * 基于 Micrometer 的观测实现。
 */
public class MicrometerErrorObservationRecorder implements ErrorObservationRecorder {

    private final MeterRegistry meterRegistry;
    private final String contextPrefix;

    public MicrometerErrorObservationRecorder(MeterRegistry meterRegistry, ErrorProperties errorProperties) {
        this.meterRegistry = meterRegistry;
        String prefix = errorProperties.getContextPrefix();
        this.contextPrefix = (prefix == null || prefix.isBlank()) ? "UNKNOWN" : prefix;
    }

    @Override
    public void recordResolution(Throwable exception, ErrorResolution resolution, long durationMs, boolean slow) {
        String exceptionName = exception == null ? "Null" : exception.getClass().getSimpleName();
        Timer.builder("papertrace.error.resolution.duration")
                .tag("context", contextPrefix)
                .tag("exception", exceptionName)
                .tag("errorCode", resolution.errorCode().code())
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);

        Counter.builder("papertrace.error.resolution.count")
                .tag("context", contextPrefix)
                .tag("errorCode", resolution.errorCode().code())
                .register(meterRegistry)
                .increment();

        if (slow) {
            Counter.builder("papertrace.error.resolution.slow")
                    .tag("context", contextPrefix)
                    .tag("exception", exceptionName)
                    .register(meterRegistry)
                    .increment();
        }
    }

    @Override
    public void recordCircuitBreakerFallback(Throwable exception) {
        String exceptionName = exception == null ? "Null" : exception.getClass().getSimpleName();
        Counter.builder("papertrace.error.resolution.circuit_breaker")
                .tag("context", contextPrefix)
                .tag("exception", exceptionName)
                .register(meterRegistry)
                .increment();
    }
}
