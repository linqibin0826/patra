package com.patra.starter.feign.error.observation;

import com.patra.starter.feign.error.config.FeignErrorProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/** Micrometer-backed implementation of {@link FeignErrorObservationRecorder}. */
@Slf4j
public class MicrometerFeignErrorObservationRecorder implements FeignErrorObservationRecorder {

  private final MeterRegistry meterRegistry;
  private final FeignErrorProperties.ObservationProperties observationProperties;

  public MicrometerFeignErrorObservationRecorder(
      MeterRegistry meterRegistry, FeignErrorProperties properties) {
    this.meterRegistry = meterRegistry;
    this.observationProperties = properties.getObservation();
  }

  @Override
  public void recordProblemDetailParsing(
      String methodKey, int status, long durationMs, boolean success) {
    Timer.builder("papertrace.feign.error.parsing")
        .tag("method", methodKey)
        .tag("status", String.valueOf(status))
        .tag("success", Boolean.toString(success))
        .register(meterRegistry)
        .record(durationMs, TimeUnit.MILLISECONDS);

    if (observationProperties.isLogSlowParsing()
        && durationMs >= observationProperties.getSlowParsingThresholdMs()) {
      log.warn(
          "Feign ProblemDetail parsing was slow: method={} status={} duration={}ms",
          methodKey,
          status,
          durationMs);
    }

    if (!success) {
      log.debug(
          "Feign ProblemDetail parsing failed: method={} status={} duration={}ms",
          methodKey,
          status,
          durationMs);
    }
  }

  @Override
  public void recordDecodingOutcome(
      String methodKey, int status, boolean success, boolean tolerantMode) {
    Counter.builder("papertrace.feign.error.decoding")
        .tag("method", methodKey)
        .tag("status", String.valueOf(status))
        .tag("success", Boolean.toString(success))
        .tag("tolerant", Boolean.toString(tolerantMode))
        .register(meterRegistry)
        .increment();

    if (tolerantMode && observationProperties.isLogTolerantUsage()) {
      log.info(
          "Feign error decoding invoked tolerant mode: method={} status={}", methodKey, status);
    }
  }

  @Override
  public void recordResponseBodyRead(
      String methodKey, int bodySize, long durationMs, boolean truncated) {
    Timer.builder("papertrace.feign.error.body.read")
        .tag("method", methodKey)
        .tag("truncated", Boolean.toString(truncated))
        .register(meterRegistry)
        .record(durationMs, TimeUnit.MILLISECONDS);

    if (observationProperties.isLogSlowBodyReading()
        && durationMs >= observationProperties.getSlowBodyReadingThresholdMs()) {
      log.warn(
          "Feign response body read was slow: method={} size={} duration={}ms truncated={}",
          methodKey,
          bodySize,
          durationMs,
          truncated);
    }
  }

  @Override
  public void recordTraceIdExtraction(String methodKey, boolean found, String headerName) {
    Counter.builder("papertrace.feign.error.traceid")
        .tag("method", methodKey)
        .tag("found", Boolean.toString(found))
        .tag("header", headerName == null ? "none" : headerName)
        .register(meterRegistry)
        .increment();

    if (found) {
      log.debug(
          "Feign response headers contained TraceId: method={} header={}", methodKey, headerName);
    } else {
      log.debug("Feign response headers did not include TraceId: method={}", methodKey);
    }
  }
}
