package com.patra.ingest.app.usecase.relay.coordinator;

import com.patra.ingest.app.usecase.relay.metrics.OutboxRelayMetrics;
import com.patra.ingest.domain.factory.OutboxRelayLogFactory;
import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.model.entity.OutboxRelayLog;
import com.patra.ingest.domain.model.enums.RelayStatus;
import com.patra.ingest.domain.model.vo.relay.RelayBatchId;
import com.patra.ingest.domain.port.OutboxRelayLogRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Relay log coordinator - manages relay execution audit trail.
 *
 * <h3>Responsibilities</h3>
 *
 * <ul>
 *   <li>Create relay execution logs using OutboxRelayLogFactory
 *   <li>Accumulate logs in memory during batch execution
 *   <li>Batch-persist logs to database for performance efficiency
 *   <li>Support all relay outcomes: PUBLISHED, DEFERRED, FAILED, LEASE_MISSED
 * </ul>
 *
 * <h3>Batch Processing Pattern</h3>
 *
 * <pre>
 * LogAccumulator acc = coordinator.createAccumulator(batchId);
 * for (OutboxMessage msg : batch) {
 *   if (!leaseAcquired) {
 *     acc.recordLeaseMissed(...);
 *   } else if (publishSuccess) {
 *     acc.recordPublished(...);
 *   } else if (retryable) {
 *     acc.recordDeferred(...);
 *   } else {
 *     acc.recordFailed(...);
 *   }
 * }
 * coordinator.persistBatch(acc); // Single INSERT with N rows
 * </pre>
 *
 * <h3>Performance Optimization</h3>
 *
 * <ul>
 *   <li>Batch insert: 100-500 logs persisted in single SQL statement
 *   <li>Reduces database round-trips from N to 1
 *   <li>Improves throughput for high-frequency relay jobs
 * </ul>
 *
 * <h3>Logging Strategy</h3>
 *
 * <ul>
 *   <li>DEBUG: Batch persist operation (size, affected rows, batch IDs)
 *   <li>No per-message logging (handled by other coordinators)
 * </ul>
 *
 * @author Papertrace Team
 * @since 2.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RelayLogCoordinator {

  private final OutboxRelayLogFactory logFactory;
  private final OutboxRelayLogRepository logRepository;
  private final OutboxRelayMetrics metrics;

  /**
   * Creates a new log accumulator for a relay batch.
   *
   * <p>The accumulator collects logs in memory during batch execution and persists them all at once
   * using {@link #persistBatch(LogAccumulator)}.
   *
   * @param batchId Relay batch identifier for grouping logs
   * @return Empty log accumulator ready to record relay outcomes
   */
  public LogAccumulator createAccumulator(RelayBatchId batchId) {
    return new LogAccumulator(batchId);
  }

  /**
   * Batch-persists accumulated relay logs to database and records metrics.
   *
   * <p>Uses single SQL INSERT with multiple rows for performance efficiency.
   *
   * <p>Example SQL generated:
   *
   * <pre>
   * INSERT INTO ing_outbox_relay_log (message_id, relay_batch_id, ...)
   * VALUES (1, 'batch-001', ...), (2, 'batch-001', ...), ...;
   * </pre>
   *
   * <p>After persisting, records metrics for each relay outcome:
   *
   * <ul>
   *   <li>PUBLISHED: {@code outbox.relay.attempts{channel=X, status=PUBLISHED}}
   *   <li>DEFERRED: {@code outbox.relay.attempts{channel=X, status=DEFERRED}} + error counter
   *   <li>FAILED: {@code outbox.relay.attempts{channel=X, status=FAILED}} + error counter
   *   <li>LEASE_MISSED: {@code outbox.relay.attempts{channel=X, status=LEASE_MISSED}}
   * </ul>
   *
   * @param accumulator Log accumulator containing collected relay logs
   */
  public void persistBatch(LogAccumulator accumulator) {
    if (accumulator.isEmpty()) {
      log.debug("No relay logs to persist for batchId={}", accumulator.batchId.getValue());
      return;
    }

    List<OutboxRelayLog> logs = accumulator.getLogs();
    logRepository.saveBatch(logs);

    // Record metrics for each relay outcome
    for (OutboxRelayLog relayLog : logs) {
      recordMetrics(relayLog);
    }
  }

  /**
   * Records metrics for a single relay log entry.
   *
   * <p>Dispatches to appropriate metric recording method based on relay status.
   *
   * @param relayLog relay log to extract metrics from
   */
  private void recordMetrics(OutboxRelayLog relayLog) {
    String channel = relayLog.getChannel();
    RelayStatus status = relayLog.getRelayStatus();

    switch (status) {
      case PUBLISHED -> metrics.recordPublished(channel);
      case DEFERRED -> metrics.recordDeferred(channel, relayLog.getErrorCode());
      case FAILED -> metrics.recordFailed(channel, relayLog.getErrorCode());
      case LEASE_MISSED -> metrics.recordLeaseMissed(channel);
      default -> log.warn("Unknown relay status for metrics: {}", status);
    }
  }

  /**
   * Log accumulator for collecting relay execution logs during batch processing.
   *
   * <p>Thread-safety: NOT thread-safe, should only be used within a single thread (typical relay
   * job execution pattern).
   */
  public class LogAccumulator {

    private final RelayBatchId batchId;
    private final List<OutboxRelayLog> logs;

    private LogAccumulator(RelayBatchId batchId) {
      this.batchId = batchId;
      this.logs = new ArrayList<>();
    }

    /**
     * Records a lease acquisition failure (concurrent competition).
     *
     * <p>Scenario: Another relay instance acquired the lease first.
     *
     * @param message Outbox message that failed to acquire lease
     * @param leaseOwner Identifier of the instance that attempted lease acquisition
     * @param startTime Relay attempt start timestamp
     */
    public void recordLeaseMissed(OutboxMessage message, String leaseOwner, Instant startTime) {
      OutboxRelayLog relayLog =
          logFactory.createForLeaseMissed(message, batchId, leaseOwner, startTime);
      logs.add(relayLog);
    }

    /**
     * Records successful message publishing.
     *
     * <p>Scenario: Message successfully sent to downstream broker.
     *
     * @param message Outbox message that was published
     * @param leaseOwner Identifier of the instance that published the message
     * @param startTime Relay attempt start timestamp
     * @param publishedAt Timestamp when message was confirmed published
     */
    public void recordPublished(
        OutboxMessage message, String leaseOwner, Instant startTime, Instant publishedAt) {
      OutboxRelayLog relayLog =
          logFactory.createForPublished(message, batchId, leaseOwner, startTime, publishedAt);
      logs.add(relayLog);
    }

    /**
     * Records deferred retry (transient error).
     *
     * <p>Scenario: Publishing failed with retryable error, will retry after backoff.
     *
     * @param message Outbox message that encountered transient error
     * @param leaseOwner Identifier of the instance that attempted publishing
     * @param startTime Relay attempt start timestamp
     * @param nextRetryAt Scheduled timestamp for next retry attempt
     * @param errorCode Error classification code (e.g., NETWORK_TIMEOUT)
     * @param errorMessage Detailed error message (will be truncated to 512 chars)
     * @param errorKind Error kind: FATAL or TRANSIENT
     */
    public void recordDeferred(
        OutboxMessage message,
        String leaseOwner,
        Instant startTime,
        Instant nextRetryAt,
        String errorCode,
        String errorMessage,
        String errorKind) {
      OutboxRelayLog relayLog =
          logFactory.createForDeferred(
              message,
              batchId,
              leaseOwner,
              startTime,
              nextRetryAt,
              errorCode,
              errorMessage,
              errorKind);
      logs.add(relayLog);
    }

    /**
     * Records permanent failure (max retries exhausted or fatal error).
     *
     * <p>Scenario: Publishing failed permanently, no further retries.
     *
     * @param message Outbox message that failed permanently
     * @param leaseOwner Identifier of the instance that attempted publishing
     * @param startTime Relay attempt start timestamp
     * @param errorCode Error classification code
     * @param errorMessage Detailed error message (will be truncated to 512 chars)
     * @param errorKind Error kind: FATAL or TRANSIENT
     */
    public void recordFailed(
        OutboxMessage message,
        String leaseOwner,
        Instant startTime,
        String errorCode,
        String errorMessage,
        String errorKind) {
      OutboxRelayLog relayLog =
          logFactory.createForFailed(
              message, batchId, leaseOwner, startTime, errorCode, errorMessage, errorKind);
      logs.add(relayLog);
    }

    /**
     * Checks if accumulator has no logs.
     *
     * @return true if no logs have been recorded
     */
    public boolean isEmpty() {
      return logs.isEmpty();
    }

    /**
     * Returns collected logs (for internal use by coordinator).
     *
     * @return Unmodifiable view of collected relay logs
     */
    List<OutboxRelayLog> getLogs() {
      return List.copyOf(logs);
    }

    /**
     * Returns the number of logs accumulated.
     *
     * @return Count of relay logs
     */
    public int size() {
      return logs.size();
    }
  }
}
