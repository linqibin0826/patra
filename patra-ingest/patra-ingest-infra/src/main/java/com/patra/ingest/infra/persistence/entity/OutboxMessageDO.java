package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <b>Outbox message DO</b> — table: <code>ing_outbox_message</code>
 *
 * <p>Semantics: Generic outbound messages persisted in the <strong>same transaction</strong> as
 * business data (task notifications / integration events). The relay scans this table only and
 * publishes to external channels (MQ/Webhook), avoiding hot business tables to ensure minimal
 * write-side intrusion and decoupled publishing.
 *
 * <p>Key rules:
 *
 * <ul>
 *   <li>Idempotency: (<code>channel</code>, <code>dedup_key</code>) is unique (UK:
 *       uk_outbox_channel_dedup) to enable source-side dedup and safe retries.
 *   <li>Ordering/partitioning: <code>partition_key</code> is recommended as "<code>
 *       provenance:operation</code>" and leveraged by index <code>
 *       idx_outbox_partition(channel, partition_key, status_code)</code> to control parallelism and
 *       preserve order (e.g., <code>PUBMED:HARVEST</code>).
 *   <li>Scheduling/delay: <code>not_before</code> is the earliest publish time (UTC); NULL means
 *       publishable anytime. Relay scans typically by <code>status_code</code> + time cursor (
 *       <code>idx_outbox_status_time</code>).
 *   <li>Lease: <code>pub_lease_owner</code>/<code>pub_leased_until</code> prevent concurrent relays
 *       from processing the same row; after expiry, another publisher may take over (<code>
 *       idx_outbox_lease</code>).
 * </ul>
 *
 * <p>Suggested state machine: <code>PENDING → PUBLISHING → PUBLISHED</code>; failures go to <code>
 * FAILED</code>. Based on backoff, compute <code>next_retry_at</code> to return to <code>PENDING
 * </code>, or mark as <code>DEAD</code> per policy.
 *
 * <p>Fields: JSON columns (<code>payload_json</code>/<code>headers_json</code>) use {@link
 * com.patra.starter.mybatis.type.JsonToJsonNodeTypeHandler JsonToJsonNodeTypeHandler} with Jackson
 * {@link com.fasterxml.jackson.databind.JsonNode JsonNode} for schemaless storage. Store only the
 * <strong>minimum necessary</strong> information (e.g.,
 * taskId/sliceKey/planKey/provenance/operation/endpoint/priority/notBefore); do not enqueue large
 * raw contents.
 *
 * <p>Audit/common fields (e.g., <code>created_at</code>/<code>version</code>) are inherited from
 * {@link BaseDO BaseDO}.
 *
 * <p>Layering: an <em>infra/persistence DO</em> in a hexagonal architecture; contains no domain
 * behavior.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ing_outbox_message", autoResultMap = true)
public class OutboxMessageDO extends BaseDO {

  /**
   * Aggregate type (e.g., TASK/PLAN/...).
   *
   * <p>Used for audit/replay and downstream grouping.
   *
   * <p>Constraint: NOT NULL; recommended values from a controlled dictionary.
   */
  @TableField("aggregate_type")
  private String aggregateType;

  /**
   * Aggregate root ID.
   *
   * <p>Task scenario: equals <code>ing_task.id</code>; other aggregates use their own primary key.
   *
   * <p>Used as the main correlation key for replay/reconciliation/diagnostics.
   */
  @TableField("aggregate_id")
  private Long aggregateId;

  /**
   * Logical channel = target topic (e.g., <code>INGEST_TASK</code>).
   *
   * <p>Combined with {@link #dedupKey} to form a unique key (UK: uk_outbox_channel_dedup).
   *
   * <p>Recommendation: keep channel taxonomy stable and limited; routing/auth handled by the
   * adapter.
   */
  @TableField("channel")
  private String channel;

  /**
   * Semantic operation tag, e.g., <code>TASK_READY</code>, <code>EVENT_PUBLISHED</code>.
   *
   * <p>Used by subscribers for routing/metrics; not part of the idempotent key.
   */
  @TableField("op_type")
  private String opType;

  /**
   * Partition/order routing key.
   *
   * <p>Recommended format: <code>"provenance:operation"</code>, e.g., <code>PUBMED:HARVEST</code>.
   *
   * <p>Controls partition concurrency and ordered publishing under the same <code>channel</code>
   * via index <code>idx_outbox_partition(channel, partition_key, status_code)</code>.
   *
   * <p>Note: not part of the uniqueness constraint; independent of {@link #dedupKey}.
   */
  @TableField("partition_key")
  private String partitionKey;

  /**
   * Deduplication key.
   *
   * <p>Uniqueness constraint: must be unique within the same {@link #channel} (UK:
   * uk_outbox_channel_dedup).
   *
   * <p>Task scenario: recommended to equal <code>ing_task.idempotent_key</code>; other scenarios
   * may use a <code>requestId</code> or content hash.
   *
   * <p>Purpose: source-side dedup and safe retries; even if consumers receive duplicates, the
   * source suppresses them.
   */
  @TableField("dedup_key")
  private String dedupKey;

  /**
   * Minimal payload (JSON).
   *
   * <p>Typical fields: <code>taskId</code>/<code>sliceKey</code>/<code>planKey</code>/<code>
   * provenance</code>/<code>operation</code>/<code>endpoint</code>/<code>priority</code>/<code>
   * notBefore</code>.
   *
   * <p>Constraint: only store <strong>minimal</strong> info required for publishing; large/raw
   * documents must not be enqueued.
   */
  @TableField("payload_json")
  private JsonNode payloadJson;

  /**
   * Extended headers (JSON), e.g., <code>correlationId</code>, tracing context.
   *
   * <p>Optional; used for cross-system correlation and troubleshooting.
   */
  @TableField("headers_json")
  private JsonNode headersJson;

  /**
   * Earliest publish time (UTC).
   *
   * <p>NULL = publishable anytime; used for scheduling/delay and rate shaping.
   */
  @TableField("not_before")
  private Instant notBefore;

  /**
   * Publish status code.
   *
   * <p>Values: <code>PENDING</code>/<code>PUBLISHING</code>/<code>PUBLISHED</code>/<code>FAILED
   * </code>/<code>DEAD</code>.
   *
   * <p>Scan strategy: typically bulk-extract by index (<code>status_code</code>, <code>not_before
   * </code>, <code>id</code>).
   */
  @TableField("status_code")
  private String statusCode;

  /**
   * Publish retry count (incremented on failures).
   *
   * <p>Use with backoff policy (exponential/Fibonacci, etc.) to compute {@link #nextRetryAt}.
   */
  @TableField("retry_count")
  private Integer retryCount;

  /**
   * Next retry publish time (UTC).
   *
   * <p>When <code>now &gt;= next_retry_at</code> and status allows, the relay can re-fetch the
   * record.
   */
  @TableField("next_retry_at")
  private Instant nextRetryAt;

  /** Last publish error code (from MQ SDK or internal policy). */
  @TableField("error_code")
  private String errorCode;

  /** Last publish error detail (truncated). */
  @TableField("error_msg")
  private String errorMsg;

  /**
   * Publisher lease owner (instance id / workerId).
   *
   * <p>Prevents duplicate publishing with multiple relay workers.
   */
  @TableField("pub_lease_owner")
  private String pubLeaseOwner;

  /**
   * Publisher lease expiry time (UTC).
   *
   * <p>Only the lease owner may process before expiry; after expiry another publisher may take
   * over.
   */
  @TableField("pub_leased_until")
  private Instant pubLeasedUntil;

  /** Message ID returned by the broker (for reconciliation/replay identification). */
  @TableField("msg_id")
  private String msgId;
}
