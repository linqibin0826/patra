package com.patra.starter.core.error.observation;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.common.error.codes.ErrorCodeLike;
import com.patra.starter.core.error.config.ErrorProperties;
import com.patra.starter.core.error.model.ErrorResolution;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MicrometerErrorObservationRecorderTest {

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

  @AfterEach
  void tearDown() {
    registry.close();
  }

  @Test
  void recordResolution_shouldRecordDurationCountersAndSlowMetric() {
    ErrorProperties props = new ErrorProperties();
    props.setContextPrefix("ING");
    MicrometerErrorObservationRecorder recorder =
        new MicrometerErrorObservationRecorder(registry, props);

    ErrorResolution resolution = new ErrorResolution(new DummyCode("ING-0400", 400), 400);
    recorder.recordResolution(new IllegalStateException("boom"), resolution, 123L, true);

    Timer timer =
        registry
            .find("papertrace.error.resolution.duration")
            .tags("context", "ING", "exception", "IllegalStateException", "errorCode", "ING-0400")
            .timer();
    assertThat(timer).isNotNull();
    assertThat(timer.count()).isEqualTo(1);
    assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(123.0d);
    Counter counter =
        registry
            .find("papertrace.error.resolution.count")
            .tags("context", "ING", "errorCode", "ING-0400")
            .counter();
    assertThat(counter).isNotNull();
    assertThat(counter.count()).isEqualTo(1.0d);

    Counter slow =
        registry
            .find("papertrace.error.resolution.slow")
            .tags("context", "ING", "exception", "IllegalStateException")
            .counter();
    assertThat(slow).isNotNull();
    assertThat(slow.count()).isEqualTo(1.0d);
  }

  @Test
  void recordCircuitBreakerFallback_shouldFallbackToUnknownContextAndNullException() {
    ErrorProperties props = new ErrorProperties();
    props.setContextPrefix("  ");
    MicrometerErrorObservationRecorder recorder =
        new MicrometerErrorObservationRecorder(registry, props);

    recorder.recordCircuitBreakerFallback(null);

    Counter fallback =
        registry
            .find("papertrace.error.resolution.circuit_breaker")
            .tags("context", "UNKNOWN", "exception", "Null")
            .counter();
    assertThat(fallback).isNotNull();
    assertThat(fallback.count()).isEqualTo(1.0d);
  }

  private record DummyCode(String code, int httpStatus) implements ErrorCodeLike {
    @Override
    public String code() {
      return code;
    }

    @Override
    public int httpStatus() {
      return httpStatus;
    }
  }
}
