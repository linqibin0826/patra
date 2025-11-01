package com.patra.ingest.domain.model.entity;

import com.patra.ingest.domain.model.enums.RelayStatus;
import java.time.Instant;
import lombok.Builder;
import lombok.Value;

/**
 * Outbox Relay execution log entity.
 *
 * <p>Records every relay attempt for outbox messages, enabling:
 *
 * <ul>
 *   <li>Historical tracing: Query complete publishing history for a message
 *   <li>Performance analysis: Analyze duration and identify bottlenecks
 *   <li>Error analysis: Identify error patterns and retry effectiveness
 *   <li>Audit compliance: Immutable audit trail for data publishing
 * </ul>
 *
 * <p>Design principles:
 *
 * <ul>
 *   <li>Immutable: Logs are never modified after creation
 *   <li>Complete: Captures all information needed for troubleshooting
 *   <li>Efficient: Optimized for append-only writes and time-range queries
 * </ul>
 *
 * @author Papertrace Team
 * @since 2.0
 */
@Value
@Builder(toBuilder = true)
public class OutboxRelayLog {

  /** Primary key (auto-increment). */
  Long id;

  /** Reference to outbox message ID. */
  Long outboxMessageId;

  /** Relay batch identifier (groups logs from same job execution). */
  String relayBatchId;

  /** Message channel (e.g., INGEST, REGISTRY). */
  String channel;

  /** Partition key for ordered message delivery. */
  String partitionKey;

  /** Lease owner identifier (host-jobId-threadId-uuid format). */
  String leaseOwner;

  /** Attempt number for this message (1-based, increments on retry). */
  Integer attemptNumber;

  /** Relay execution result: PUBLISHED/DEFERRED/FAILED/LEASE_MISSED. */
  RelayStatus relayStatus;

  /** Error code if relay failed (e.g., NETWORK_TIMEOUT, BROKER_UNAVAILABLE). */
  String errorCode;

  /** Error details if relay failed (truncated to 512 characters). */
  String errorMessage;

  /** Error classification: FATAL or TRANSIENT (determines retry eligibility). */
  String errorKind;

  /** Relay start timestamp (UTC). */
  Instant startedAt;

  /** Relay completion timestamp (UTC). */
  Instant completedAt;

  /** Execution duration in milliseconds (completedAt - startedAt). */
  Integer durationMs;

  /** Next retry timestamp (only present for DEFERRED status). */
  Instant nextRetryAt;

  /**
   * Checks if this relay attempt succeeded.
   *
   * @return true if relay status is PUBLISHED
   */
  public boolean isPublished() {
    return relayStatus == RelayStatus.PUBLISHED;
  }

  /**
   * Checks if this relay attempt failed permanently.
   *
   * @return true if relay status is FAILED
   */
  public boolean isFailed() {
    return relayStatus == RelayStatus.FAILED;
  }

  /**
   * Checks if this relay attempt was deferred for retry.
   *
   * @return true if relay status is DEFERRED
   */
  public boolean isDeferred() {
    return relayStatus == RelayStatus.DEFERRED;
  }

  /**
   * Checks if this relay attempt lost lease competition.
   *
   * @return true if relay status is LEASE_MISSED
   */
  public boolean isLeaseMissed() {
    return relayStatus == RelayStatus.LEASE_MISSED;
  }

  /**
   * Checks if this relay status represents a terminal state (no further processing needed).
   *
   * @return true if status is terminal (PUBLISHED or FAILED)
   */
  public boolean isTerminal() {
    return relayStatus != null && relayStatus.isTerminal();
  }

  /**
   * Checks if this relay status indicates a retryable failure.
   *
   * @return true if status is retryable (DEFERRED or LEASE_MISSED)
   */
  public boolean isRetryable() {
    return relayStatus != null && relayStatus.isRetryable();
  }

  /**
   * Validates that this relay log has all required fields.
   *
   * @throws IllegalStateException if any required field is null
   */
  public void validate() {
    if (outboxMessageId == null) {
      throw new IllegalStateException("outboxMessageId must not be null");
    }
    if (relayBatchId == null || relayBatchId.isBlank()) {
      throw new IllegalStateException("relayBatchId must not be null or blank");
    }
    if (channel == null || channel.isBlank()) {
      throw new IllegalStateException("channel must not be null or blank");
    }
    if (attemptNumber == null || attemptNumber < 1) {
      throw new IllegalStateException("attemptNumber must be >= 1");
    }
    if (relayStatus == null) {
      throw new IllegalStateException("relayStatus must not be null");
    }
    if (startedAt == null) {
      throw new IllegalStateException("startedAt must not be null");
    }
    if (completedAt == null) {
      throw new IllegalStateException("completedAt must not be null");
    }
    if (durationMs == null || durationMs < 0) {
      throw new IllegalStateException("durationMs must be >= 0");
    }
  }
}
