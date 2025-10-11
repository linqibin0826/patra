package com.patra.starter.core.error.observation;

import com.patra.starter.core.error.model.ErrorResolution;

/**
 * Abstraction for publishing metrics or telemetry captured during error resolution.
 */
public interface ErrorObservationRecorder {

    /**
     * Records the outcome and timing of a single error-resolution run.
     *
     * @param exception  the original exception being resolved
     * @param resolution the resulting normalized error
     * @param durationMs time spent resolving the error in milliseconds
     * @param slow       whether the execution exceeded the configured slow threshold
     */
    void recordResolution(Throwable exception, ErrorResolution resolution, long durationMs, boolean slow);

    /**
     * Records that the circuit breaker produced a fallback response instead of executing the
     * pipeline.
     */
    void recordCircuitBreakerFallback(Throwable exception);

    /**
     * No-op implementation convenient for dependency injection when observations are disabled.
     */
    ErrorObservationRecorder NO_OP = new ErrorObservationRecorder() {
        @Override
        public void recordResolution(Throwable exception, ErrorResolution resolution, long durationMs, boolean slow) {
            // no-op
        }

        @Override
        public void recordCircuitBreakerFallback(Throwable exception) {
            // no-op
        }
    };
}
