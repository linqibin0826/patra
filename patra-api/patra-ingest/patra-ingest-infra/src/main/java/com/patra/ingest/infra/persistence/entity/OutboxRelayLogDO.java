package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <b>Outbox relay log DO</b> — table: <code>ing_outbox_relay_log</code>
 *
 * <p>Semantics: Records every relay execution attempt for outbox messages, providing a complete
 * audit trail for troubleshooting, performance analysis, and compliance.
 *
 * <p>Key characteristics:
 *
 * <ul>
 *   <li><strong>Immutable</strong>: Logs are never updated after creation (append-only audit trail)
 *   <li><strong>Complete</strong>: Captures all information needed for debugging (timing, errors,
 *       context)
 *   <li><strong>Indexed</strong>: Optimized for common query patterns (by message, by batch, by
 *       time range)
 * </ul>
 *
 * <p>Use cases:
 *
 * <ul>
 *   <li>Historical tracing: Query complete relay history for a specific message
 *   <li>Performance analysis: Analyze relay duration and identify bottlenecks
 *   <li>Error analysis: Identify error patterns and retry effectiveness
 *   <li>Batch statistics: Aggregate success rates and performance metrics per batch
 *   <li>Monitoring: Real-time alerts on failed relays or performance degradation
 * </ul>
 *
 * <p>Relationship with {@link OutboxMessageDO}:
 *
 * <ul>
 *   <li>One outbox message can have multiple relay log entries (one per attempt)
 *   <li>Referential integrity enforced at <strong>application layer</strong> (no database FK for
 *       performance)
 *   <li>Denormalizes {@code channel} and {@code partitionKey} from outbox message for query
 *       efficiency
 * </ul>
 *
 * <p>Relay status values:
 *
 * <ul>
 *   <li><code>PUBLISHED</code>: Successfully published to downstream broker (terminal state)
 *   <li><code>DEFERRED</code>: Failed with retryable error, scheduled for retry (transient state)
 *   <li><code>FAILED</code>: Permanently failed after max retries or fatal error (terminal state)
 *   <li><code>LEASE_MISSED</code>: Failed to acquire lease due to concurrent competition
 * </ul>
 *
 * <p>Audit/common fields (e.g., <code>created_at</code>, <code>version</code>, <code>deleted
 * </code>) are inherited from {@link BaseDO BaseDO}.
 *
 * <p>Layering: an <em>infra/persistence DO</em> in a hexagonal architecture; contains no domain
 * behavior.
 *
 * @author Papertrace Team
 * @since 2.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ing_outbox_relay_log", autoResultMap = true)
public class OutboxRelayLogDO extends BaseDO {

  /**
   * Reference to outbox message ID.
   *
   * <p>Logical FK to {@link OutboxMessageDO#getId() ing_outbox_message.id}; integrity enforced at
   * application layer.
   *
   * <p>Constraint: NOT NULL; must reference an existing outbox message.
   */
  @TableField("message_id")
  private Long messageId;

  /**
   * Relay batch identifier (format: yyyyMMddHHmmss-xxxxxxxx).
   *
   * <p>Groups all relay logs from the same job execution (e.g., scheduled relay job run).
   *
   * <p>Example: <code>20251031150000-a1b2c3d4</code>
   *
   * <p>Use cases:
   *
   * <ul>
   *   <li>Batch-level statistics (success rate, average duration)
   *   <li>Troubleshooting batch failures
   *   <li>Performance analysis per batch
   * </ul>
   */
  @TableField("relay_batch_id")
  private String relayBatchId;

  /**
   * Message channel (denormalized from {@link OutboxMessageDO#getChannel()}).
   *
   * <p>Denormalized for query efficiency (avoid JOIN with outbox_message table).
   *
   * <p>Example: <code>INGEST_TASK</code>, <code>REGISTRY_UPDATE</code>
   */
  @TableField("channel")
  private String channel;

  /**
   * Partition key (denormalized from {@link OutboxMessageDO#getPartitionKey()}).
   *
   * <p>Used for analysis and troubleshooting partition-specific issues.
   *
   * <p>Example: <code>PUBMED:HARVEST</code>
   */
  @TableField("partition_key")
  private String partitionKey;

  /**
   * Lease owner identifier (host-jobId-threadId-uuid format).
   *
   * <p>Identifies which instance/thread attempted this relay (for distributed troubleshooting).
   *
   * <p>Example: <code>ingest-server-1-job123-thread456-a1b2c3d4</code>
   */
  @TableField("lease_owner")
  private String leaseOwner;

  /**
   * Attempt number for this message (1-based).
   *
   * <p>First attempt = 1, increments on retry.
   *
   * <p>Use cases:
   *
   * <ul>
   *   <li>Retry analysis (how many attempts before success?)
   *   <li>Performance analysis (do later attempts take longer?)
   *   <li>Alerting (trigger alert on attempt > threshold)
   * </ul>
   */
  @TableField("attempt_number")
  private Integer attemptNumber;

  /**
   * Relay execution result status.
   *
   * <p>Values:
   *
   * <ul>
   *   <li><code>PUBLISHED</code>: Success (terminal state)
   *   <li><code>DEFERRED</code>: Transient error, will retry (transient state)
   *   <li><code>FAILED</code>: Permanent failure (terminal state)
   *   <li><code>LEASE_MISSED</code>: Concurrent lease conflict (transient state)
   * </ul>
   *
   * <p>Indexed for alert queries (e.g., "show all FAILED relays in last hour").
   */
  @TableField("relay_status")
  private String relayStatus;

  /**
   * Error code if relay failed (NULL for success).
   *
   * <p>Examples: <code>NETWORK_TIMEOUT</code>, <code>BROKER_UNAVAILABLE</code>, <code>
   * SERIALIZATION_ERROR</code>
   *
   * <p>Use cases:
   *
   * <ul>
   *   <li>Error distribution analysis (which errors are most common?)
   *   <li>Root cause analysis (group failures by error code)
   *   <li>Alert routing (send different alerts for different error types)
   * </ul>
   */
  @TableField("error_code")
  private String errorCode;

  /**
   * Error details if relay failed (NULL for success, truncated to 512 chars).
   *
   * <p>Contains detailed error message and stack trace excerpt.
   *
   * <p>Note: Application layer truncates to 512 characters to prevent excessive storage.
   */
  @TableField("error_message")
  private String errorMessage;

  /**
   * Error classification: FATAL or TRANSIENT (NULL for success).
   *
   * <p>Values:
   *
   * <ul>
   *   <li><code>FATAL</code>: Non-retryable error (e.g., invalid payload, authentication failure)
   *   <li><code>TRANSIENT</code>: Retryable error (e.g., network timeout, broker unavailable)
   * </ul>
   *
   * <p>Used for retry decision-making and alerting.
   */
  @TableField("error_kind")
  private String errorKind;

  /**
   * Relay start timestamp (UTC).
   *
   * <p>Marks when relay execution began (before lease acquisition).
   *
   * <p>Indexed for time-range queries (e.g., "show all relays in last 24 hours").
   */
  @TableField("started_at")
  private Instant startedAt;

  /**
   * Relay completion timestamp (UTC).
   *
   * <p>Marks when relay execution finished (after publish or error handling).
   */
  @TableField("completed_at")
  private Instant completedAt;

  /**
   * Relay execution duration in milliseconds (completedAt - startedAt).
   *
   * <p>Use cases:
   *
   * <ul>
   *   <li>Performance analysis (P50, P95, P99 latency)
   *   <li>Bottleneck identification (which messages take longest?)
   *   <li>SLA monitoring (trigger alert if duration > threshold)
   * </ul>
   */
  @TableField("duration_ms")
  private Integer durationMs;

  /**
   * Next retry timestamp (UTC), only present for DEFERRED status.
   *
   * <p>NULL for all other statuses (PUBLISHED, FAILED, LEASE_MISSED).
   *
   * <p>Computed using exponential backoff policy based on attempt number.
   */
  @TableField("next_retry_at")
  private Instant nextRetryAt;
}
