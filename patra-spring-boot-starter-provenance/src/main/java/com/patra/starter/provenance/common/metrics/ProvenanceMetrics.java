package com.patra.starter.provenance.common.metrics;

import com.patra.common.enums.ProvenanceCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Objects;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * Provenance metrics recorder.
 *
 * <p>Wraps provenance API calls with Micrometer timers and counters so that downstream services can
 * monitor latency and error rate per data source.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
public class ProvenanceMetrics {

  private final MeterRegistry meterRegistry;

  public ProvenanceMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry cannot be null");
  }

  /**
   * Record a provenance API call with success/failure metrics.
   *
   * @param provenanceCode provenance source identifier
   * @param apiName API name (esearch, efetch, search ...)
   * @param supplier API invocation logic
   * @param <T> result type
   * @return supplier result
   */
  public <T> T recordApiCall(ProvenanceCode provenanceCode, String apiName, Supplier<T> supplier) {
    Objects.requireNonNull(provenanceCode, "provenanceCode cannot be null");
    Objects.requireNonNull(apiName, "apiName cannot be null");
    Objects.requireNonNull(supplier, "supplier cannot be null");

    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      T result = supplier.get();
      recordTimer(provenanceCode, apiName, "success", sample);
      incrementCounter("provenance.client.api.success", provenanceCode, apiName, null);
      return result;
    } catch (RuntimeException ex) {
      recordTimer(provenanceCode, apiName, "failure", sample);
      incrementCounter(
          "provenance.client.api.failure", provenanceCode, apiName, ex.getClass().getSimpleName());
      log.debug(
          "API call failed: provenance={} api={} error={}",
          provenanceCode.getCode(),
          apiName,
          ex.getClass().getSimpleName());
      throw ex;
    }
  }

  private void recordTimer(
      ProvenanceCode code, String apiName, String status, Timer.Sample sample) {
    sample.stop(
        Timer.builder("provenance.client.api.duration")
            .tag("provenanceCode", code.getCode())
            .tag("apiName", apiName)
            .tag("status", status)
            .register(meterRegistry));
  }

  private void incrementCounter(
      String metricName, ProvenanceCode code, String apiName, String errorType) {
    incrementCounter(metricName, code, apiName, errorType, 1.0d);
  }

  private void incrementCounter(
      String metricName, ProvenanceCode code, String apiName, String errorType, double amount) {
    Counter.Builder builder =
        Counter.builder(metricName).tag("provenanceCode", code.getCode()).tag("apiName", apiName);
    if (errorType != null) {
      builder.tag("errorType", errorType);
    }
    builder.register(meterRegistry).increment(amount);
  }

  private void incrementCounter(String metricName, ProvenanceCode code, double amount) {
    Counter.builder(metricName)
        .tag("provenanceCode", code.getCode())
        .register(meterRegistry)
        .increment(amount);
  }

  /**
   * Records conversion metrics for standardized literature conversion.
   *
   * @param code provenance source
   * @param successCount number of successfully converted records
   * @param failureCount number of failed conversions
   */
  public void recordConversionMetrics(ProvenanceCode code, int successCount, int failureCount) {
    if (successCount > 0) {
      incrementCounter("provenance.conversion.success", code, successCount);
    }
    if (failureCount > 0) {
      incrementCounter("provenance.conversion.failure", code, failureCount);
    }
  }
}
