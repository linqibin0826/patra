package com.patra.ingest.domain.factory;

import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.model.entity.OutboxRelayLog;
import com.patra.ingest.domain.model.enums.RelayStatus;
import com.patra.ingest.domain.model.vo.relay.RelayBatchId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Factory for creating OutboxRelayLog domain entities.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Encapsulate relay log construction logic in Domain layer
 *   <li>Use OutboxMessage's domain methods (computeNextAttempt, etc.)
 *   <li>Ensure consistent log creation across different relay outcomes
 * </ul>
 *
 * <p>Design principles:
 *
 * <ul>
 *   <li><strong>Pure Java</strong>: No Spring dependencies (NO @Component annotation)
 *   <li><strong>Instance factory</strong>: Injectable Clock for deterministic testing
 *   <li><strong>Single responsibility</strong>: Only constructs OutboxRelayLog instances
 *   <li><strong>Domain rules</strong>: Delegates to OutboxMessage for business logic
 * </ul>
 *
 * <p>Usage:
 *
 * <pre>{@code
 * // Boot layer registers as bean:
 * @Bean
 * public OutboxRelayLogFactory factory(Clock clock) {
 *   return new OutboxRelayLogFactory(clock);
 * }
 *
 * // Application layer uses injected factory:
 * OutboxRelayLog log = factory.createForPublished(message, batchId, ...);
 * }</pre>
 *
 * @author Papertrace Team
 * @since 2.0
 */
public class OutboxRelayLogFactory {

  private final Clock clock;

  /**
   * Constructor for factory instance.
   *
   * @param clock Clock instance for timestamp generation (injectable for testing)
   */
  public OutboxRelayLogFactory(Clock clock) {
    this.clock = clock;
  }

  /**
   * Creates relay log for lease acquisition failure (concurrent competition).
   *
   * <p>Scenario: Another instance acquired the lease before this instance could.
   *
   * @param message Outbox message that failed to acquire lease
   * @param batchId Relay batch identifier
   * @param leaseOwner Identifier of the instance that attempted lease acquisition
   * @param startTime Relay attempt start timestamp
   * @return OutboxRelayLog with LEASE_MISSED status
   */
  public OutboxRelayLog createForLeaseMissed(
      OutboxMessage message, RelayBatchId batchId, String leaseOwner, Instant startTime) {

    Instant completedAt = Instant.now(clock);

    return OutboxRelayLog.builder()
        .outboxMessageId(message.getId())
        .relayBatchId(batchId.getValue())
        .channel(message.getChannel())
        .partitionKey(message.getPartitionKey())
        .leaseOwner(leaseOwner)
        .attemptNumber(message.computeNextAttempt()) // ✅ Uses domain method
        .relayStatus(RelayStatus.LEASE_MISSED)
        .startedAt(startTime)
        .completedAt(completedAt)
        .durationMs((int) Duration.between(startTime, completedAt).toMillis())
        .build();
  }

  /**
   * Creates relay log for successful message publishing.
   *
   * <p>Scenario: Message successfully sent to downstream broker.
   *
   * @param message Outbox message that was published
   * @param batchId Relay batch identifier
   * @param leaseOwner Identifier of the instance that published the message
   * @param startTime Relay attempt start timestamp
   * @param publishedAt Timestamp when message was confirmed published
   * @return OutboxRelayLog with PUBLISHED status
   */
  public OutboxRelayLog createForPublished(
      OutboxMessage message,
      RelayBatchId batchId,
      String leaseOwner,
      Instant startTime,
      Instant publishedAt) {

    return OutboxRelayLog.builder()
        .outboxMessageId(message.getId())
        .relayBatchId(batchId.getValue())
        .channel(message.getChannel())
        .partitionKey(message.getPartitionKey())
        .leaseOwner(leaseOwner)
        .attemptNumber(message.computeNextAttempt()) // ✅ Uses domain method
        .relayStatus(RelayStatus.PUBLISHED)
        .startedAt(startTime)
        .completedAt(publishedAt)
        .durationMs((int) Duration.between(startTime, publishedAt).toMillis())
        .build();
  }

  /**
   * Creates relay log for deferred retry (transient error).
   *
   * <p>Scenario: Publishing failed with retryable error, will retry after backoff.
   *
   * @param message Outbox message that encountered transient error
   * @param batchId Relay batch identifier
   * @param leaseOwner Identifier of the instance that attempted publishing
   * @param startTime Relay attempt start timestamp
   * @param nextRetryAt Scheduled timestamp for next retry attempt
   * @param errorCode Error classification code (e.g., NETWORK_TIMEOUT)
   * @param errorMessage Detailed error message (truncated to 512 chars)
   * @param errorKind Error kind: FATAL or TRANSIENT
   * @return OutboxRelayLog with DEFERRED status
   */
  public OutboxRelayLog createForDeferred(
      OutboxMessage message,
      RelayBatchId batchId,
      String leaseOwner,
      Instant startTime,
      Instant nextRetryAt,
      String errorCode,
      String errorMessage,
      String errorKind) {

    Instant completedAt = Instant.now(clock);

    return OutboxRelayLog.builder()
        .outboxMessageId(message.getId())
        .relayBatchId(batchId.getValue())
        .channel(message.getChannel())
        .partitionKey(message.getPartitionKey())
        .leaseOwner(leaseOwner)
        .attemptNumber(message.computeNextAttempt()) // ✅ Uses domain method
        .relayStatus(RelayStatus.DEFERRED)
        .errorCode(errorCode)
        .errorMessage(truncate(errorMessage, 512))
        .errorKind(errorKind)
        .nextRetryAt(nextRetryAt)
        .startedAt(startTime)
        .completedAt(completedAt)
        .durationMs((int) Duration.between(startTime, completedAt).toMillis())
        .build();
  }

  /**
   * Creates relay log for permanent failure (max retries exhausted or fatal error).
   *
   * <p>Scenario: Publishing failed permanently, no further retries.
   *
   * @param message Outbox message that failed permanently
   * @param batchId Relay batch identifier
   * @param leaseOwner Identifier of the instance that attempted publishing
   * @param startTime Relay attempt start timestamp
   * @param errorCode Error classification code
   * @param errorMessage Detailed error message (truncated to 512 chars)
   * @param errorKind Error kind: FATAL or TRANSIENT
   * @return OutboxRelayLog with FAILED status
   */
  public OutboxRelayLog createForFailed(
      OutboxMessage message,
      RelayBatchId batchId,
      String leaseOwner,
      Instant startTime,
      String errorCode,
      String errorMessage,
      String errorKind) {

    Instant completedAt = Instant.now(clock);

    return OutboxRelayLog.builder()
        .outboxMessageId(message.getId())
        .relayBatchId(batchId.getValue())
        .channel(message.getChannel())
        .partitionKey(message.getPartitionKey())
        .leaseOwner(leaseOwner)
        .attemptNumber(message.computeNextAttempt()) // ✅ Uses domain method
        .relayStatus(RelayStatus.FAILED)
        .errorCode(errorCode)
        .errorMessage(truncate(errorMessage, 512))
        .errorKind(errorKind)
        .startedAt(startTime)
        .completedAt(completedAt)
        .durationMs((int) Duration.between(startTime, completedAt).toMillis())
        .build();
  }

  /**
   * Truncates error message to maximum length.
   *
   * @param str original string
   * @param maxLength maximum allowed length
   * @return truncated string (or null if input is null)
   */
  private String truncate(String str, int maxLength) {
    if (str == null) {
      return null;
    }
    return str.length() > maxLength ? str.substring(0, maxLength) : str;
  }
}
