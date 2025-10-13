package com.patra.ingest.app.outbox.metrics;

import com.patra.ingest.app.outbox.config.OutboxPublisherProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Metrics facade for Outbox publishing operations.
 *
 * <p>Provides Micrometer instrumentation for:
 *
 * <ul>
 *   <li>papertrace.outbox.publish.total (Counter): Total publish attempts with status tags
 *   <li>papertrace.outbox.publish.duration (Timer): Publish operation duration
 *   <li>papertrace.outbox.publish.batch.size (DistributionSummary): Batch size distribution
 * </ul>
 *
 * <p>All metrics include tags: aggregateType (controlled by allowedAggregateTypes), opType.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Component
public class OutboxMetrics {

  private final MeterRegistry meterRegistry;
  private final OutboxPublisherProperties properties;

  /**
   * Constructs OutboxMetrics with Micrometer registry and configuration.
   *
   * @param meterRegistry Micrometer meter registry (injected by Spring)
   * @param properties Outbox publisher configuration properties
   */
  public OutboxMetrics(MeterRegistry meterRegistry, OutboxPublisherProperties properties) {
    this.meterRegistry = meterRegistry;
    this.properties = properties;
  }

  /**
   * Records a publish operation result (success or failure).
   *
   * <p>Metrics recorded:
   *
   * <ul>
   *   <li>papertrace.outbox.publish.total (Counter): Incremented on each publish attempt
   *   <li>papertrace.outbox.publish.duration (Timer): Records operation duration
   * </ul>
   *
   * @param aggregateType Aggregate type (e.g., "Task", "LiteratureData"), must be in allowed list
   * @param opType Operation type (e.g., "batch", "retry", "CREATED")
   * @param isSuccess true if operation succeeded, false otherwise
   * @param duration Operation duration (must not be null)
   */
  public void recordPublish(
      String aggregateType, String opType, boolean isSuccess, Duration duration) {
    if (!properties.getMetrics().isEnabled()) {
      return;
    }

    try {
      validateAggregateType(aggregateType);

      String status = isSuccess ? "success" : "failure";

      // Counter: papertrace.outbox.publish.total
      Counter.builder("papertrace.outbox.publish.total")
          .tag("aggregateType", aggregateType)
          .tag("opType", opType)
          .tag("status", status)
          .register(meterRegistry)
          .increment();

      // Timer: papertrace.outbox.publish.duration
      Timer.builder("papertrace.outbox.publish.duration")
          .tag("aggregateType", aggregateType)
          .tag("opType", opType)
          .register(meterRegistry)
          .record(duration);

    } catch (Exception e) {
      log.warn(
          "Failed to record Outbox metrics for aggregateType={}, opType={}: {}",
          aggregateType,
          opType,
          e.getMessage());
    }
  }

  /**
   * Records batch size distribution.
   *
   * <p>Useful for analyzing batch processing patterns and identifying optimization opportunities.
   *
   * @param aggregateType Aggregate type (must be in allowed list)
   * @param batchSize Size of the batch being recorded (should be positive)
   */
  public void recordBatchSize(String aggregateType, int batchSize) {
    if (!properties.getMetrics().isEnabled()) {
      return;
    }

    try {
      validateAggregateType(aggregateType);

      DistributionSummary.builder("papertrace.outbox.publish.batch.size")
          .tag("aggregateType", aggregateType)
          .register(meterRegistry)
          .record(batchSize);

    } catch (Exception e) {
      log.warn(
          "Failed to record batch size metric for aggregateType={}: {}",
          aggregateType,
          e.getMessage());
    }
  }

  /**
   * Validates aggregate type against allowed list to prevent metric cardinality explosion.
   *
   * @param aggregateType Aggregate type to validate
   * @throws IllegalArgumentException if aggregate type is not allowed
   */
  private void validateAggregateType(String aggregateType) {
    Set<String> allowed = properties.getAllowedAggregateTypes();
    if (!allowed.isEmpty() && !allowed.contains(aggregateType)) {
      throw new IllegalArgumentException(
          String.format(
              "Unknown aggregateType '%s'. Allowed types: %s. "
                  + "Update papertrace.outbox.publisher.allowed-aggregate-types in configuration.",
              aggregateType, allowed));
    }
  }
}
