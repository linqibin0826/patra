package com.patra.ingest.app.usecase.relay.metrics;

import com.patra.ingest.domain.model.enums.RelayStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Outbox relay metrics collector using Micrometer.
 *
 * <h3>Metrics Overview</h3>
 *
 * <p>This component exposes the following metrics for monitoring and alerting:
 *
 * <ul>
 *   <li><strong>outbox.relay.attempts</strong> (Counter): Total relay attempts by status and
 *       channel
 *   <li><strong>outbox.relay.duration</strong> (Timer): Relay execution duration distribution
 *   <li><strong>outbox.relay.batch.size</strong> (DistributionSummary): Batch size distribution
 *   <li><strong>outbox.relay.errors</strong> (Counter): Error count by error code and channel
 * </ul>
 *
 * <h3>Tag Dimensions</h3>
 *
 * <ul>
 *   <li><code>channel</code>: Outbox channel (e.g., INGEST_TASK, REGISTRY_UPDATE)
 *   <li><code>status</code>: Relay result status (PUBLISHED, DEFERRED, FAILED, LEASE_MISSED)
 *   <li><code>error_code</code>: Error classification code (for failures only)
 * </ul>
 *
 * <h3>Usage Example</h3>
 *
 * <pre>{@code
 * // Record successful publish
 * metrics.recordPublished("INGEST_TASK");
 *
 * // Record deferred retry
 * metrics.recordDeferred("INGEST_TASK", "NETWORK_TIMEOUT");
 *
 * // Record batch execution
 * metrics.recordBatchSize(150);
 * metrics.recordBatchDuration("INGEST_TASK", Duration.ofMillis(250));
 * }</pre>
 *
 * @author Papertrace Team
 * @since 2.0
 */
@Slf4j
@Component
public class OutboxRelayMetrics {

  private static final String METRIC_PREFIX = "outbox.relay";
  private static final String TAG_CHANNEL = "channel";
  private static final String TAG_STATUS = "status";
  private static final String TAG_ERROR_CODE = "error_code";

  private final MeterRegistry meterRegistry;

  public OutboxRelayMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    log.info(
        "Initialized OutboxRelayMetrics with registry: {}",
        meterRegistry.getClass().getSimpleName());
  }

  /**
   * Records a successful message publish.
   *
   * <p>Increments counter: {@code outbox.relay.attempts{channel=X, status=PUBLISHED}}
   *
   * @param channel outbox channel name
   */
  public void recordPublished(String channel) {
    counter("attempts", channel, RelayStatus.PUBLISHED.getCode()).increment();
  }

  /**
   * Records a deferred retry (transient error).
   *
   * <p>Increments counters:
   *
   * <ul>
   *   <li>{@code outbox.relay.attempts{channel=X, status=DEFERRED}}
   *   <li>{@code outbox.relay.errors{channel=X, error_code=Y}}
   * </ul>
   *
   * @param channel outbox channel name
   * @param errorCode error classification code
   */
  public void recordDeferred(String channel, String errorCode) {
    counter("attempts", channel, RelayStatus.DEFERRED.getCode()).increment();
    errorCounter(channel, errorCode).increment();
  }

  /**
   * Records a permanent failure.
   *
   * <p>Increments counters:
   *
   * <ul>
   *   <li>{@code outbox.relay.attempts{channel=X, status=FAILED}}
   *   <li>{@code outbox.relay.errors{channel=X, error_code=Y}}
   * </ul>
   *
   * @param channel outbox channel name
   * @param errorCode error classification code
   */
  public void recordFailed(String channel, String errorCode) {
    counter("attempts", channel, RelayStatus.FAILED.getCode()).increment();
    errorCounter(channel, errorCode).increment();
  }

  /**
   * Records a lease acquisition miss (concurrent competition).
   *
   * <p>Increments counter: {@code outbox.relay.attempts{channel=X, status=LEASE_MISSED}}
   *
   * @param channel outbox channel name
   */
  public void recordLeaseMissed(String channel) {
    counter("attempts", channel, RelayStatus.LEASE_MISSED.getCode()).increment();
  }

  /**
   * Records batch execution duration.
   *
   * <p>Records timer: {@code outbox.relay.duration{channel=X}}
   *
   * <p>Useful for monitoring:
   *
   * <ul>
   *   <li>P50, P95, P99 latency
   *   <li>Performance degradation over time
   *   <li>SLA compliance (e.g., 95% of batches complete within 500ms)
   * </ul>
   *
   * @param channel outbox channel name
   * @param duration batch execution duration
   */
  public void recordBatchDuration(String channel, Duration duration) {
    timer(channel).record(duration.toMillis(), TimeUnit.MILLISECONDS);
  }

  /**
   * Records batch size (number of messages processed).
   *
   * <p>Records distribution summary: {@code outbox.relay.batch.size}
   *
   * <p>Useful for monitoring:
   *
   * <ul>
   *   <li>Average batch size
   *   <li>Throughput estimation (batch size × batch frequency)
   *   <li>Resource utilization (larger batches = better efficiency)
   * </ul>
   *
   * @param batchSize number of messages in batch
   */
  public void recordBatchSize(int batchSize) {
    batchSizeSummary().record(batchSize);
  }

  /**
   * Gets or creates a counter for relay attempts.
   *
   * <p>Counter name: {@code outbox.relay.attempts}
   *
   * <p>Tags:
   *
   * <ul>
   *   <li>channel: Outbox channel name
   *   <li>status: Relay result status
   * </ul>
   */
  private Counter counter(String metric, String channel, String status) {
    return Counter.builder(METRIC_PREFIX + "." + metric)
        .tag(TAG_CHANNEL, channel)
        .tag(TAG_STATUS, status)
        .description("Outbox relay attempts by status and channel")
        .register(meterRegistry);
  }

  /**
   * Gets or creates a counter for relay errors.
   *
   * <p>Counter name: {@code outbox.relay.errors}
   *
   * <p>Tags:
   *
   * <ul>
   *   <li>channel: Outbox channel name
   *   <li>error_code: Error classification code
   * </ul>
   */
  private Counter errorCounter(String channel, String errorCode) {
    return Counter.builder(METRIC_PREFIX + ".errors")
        .tag(TAG_CHANNEL, channel)
        .tag(TAG_ERROR_CODE, errorCode)
        .description("Outbox relay errors by error code and channel")
        .register(meterRegistry);
  }

  /**
   * Gets or creates a timer for batch execution duration.
   *
   * <p>Timer name: {@code outbox.relay.duration}
   *
   * <p>Tags:
   *
   * <ul>
   *   <li>channel: Outbox channel name
   * </ul>
   */
  private Timer timer(String channel) {
    return Timer.builder(METRIC_PREFIX + ".duration")
        .tag(TAG_CHANNEL, channel)
        .description("Outbox relay batch execution duration")
        .publishPercentiles(0.5, 0.95, 0.99) // P50, P95, P99
        .register(meterRegistry);
  }

  /**
   * Gets or creates a distribution summary for batch size.
   *
   * <p>Summary name: {@code outbox.relay.batch.size}
   *
   * <p>No tags (global metric across all channels).
   */
  private DistributionSummary batchSizeSummary() {
    return DistributionSummary.builder(METRIC_PREFIX + ".batch.size")
        .description("Outbox relay batch size distribution")
        .publishPercentiles(0.5, 0.95, 0.99) // P50, P95, P99
        .register(meterRegistry);
  }
}
