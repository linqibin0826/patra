package com.patra.starter.core.error.pipeline.interceptor;

import com.patra.starter.core.error.config.ErrorProperties;
import com.patra.starter.core.error.model.ErrorResolution;
import com.patra.starter.core.error.observation.ErrorObservationRecorder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsInterceptorTest {

    @Test
    void should_record_metrics_and_delegate() {
        ErrorProperties.ObservationProperties op = new ErrorProperties.ObservationProperties();
        op.setSlowThresholdMs(0); // treat every invocation as slow
        op.setLogSlowResolution(false);

        final boolean[] called = {false};
        ErrorObservationRecorder recorder = new ErrorObservationRecorder() {
            @Override public void recordResolution(Throwable exception, ErrorResolution resolution, long durationMs, boolean slow) {
                called[0] = true;
            }
            @Override public void recordCircuitBreakerFallback(Throwable exception) { }
        };

        MetricsInterceptor interceptor = new MetricsInterceptor(recorder, op);
        ErrorResolution r = interceptor.intercept(new RuntimeException(), ex -> new ErrorResolution(new com.patra.common.error.codes.ErrorCodeLike() {
            @Override public String code() { return "ING-0404"; }
            @Override public int httpStatus() { return 404; }
        }, 404));
        assertThat(r.httpStatus()).isEqualTo(404);
        assertThat(called[0]).isTrue();
    }
}
