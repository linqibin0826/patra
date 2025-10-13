package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <b>Cursor advancement event DO</b> — table: <code>ing_cursor_event</code>
 *
 * <p>Append-only audit trail recording each advancement from old to new cursor values, supporting
 * replay and end-to-end tracing.
 *
 * <p>Notes:
 *
 * <ul>
 *   <li><code>idempotent_key</code> is unique (UK: uk_cur_evt_idem) to prevent duplicate writes.
 *   <li>Persist both time/numeric normalized values (prev/new + observed) for cross-type ordering
 *       and statistics.
 *   <li>Redundant lineage fields chain schedule → plan → slice → task → run → batch for
 *       troubleshooting.
 *   <li><code>direction_code</code> marks BACKFILL vs FORWARD progression.
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ing_cursor_event")
public class CursorEventDO extends BaseDO {

  /** Provenance code. */
  @TableField("provenance_code")
  private String provenanceCode;

  /** Operation type code. */
  @TableField("operation_code")
  private String operationCode;

  /** Cursor logical key. */
  @TableField("cursor_key")
  private String cursorKey;

  /** Namespace scope. */
  @TableField("namespace_scope_code")
  private String namespaceScopeCode;

  /** Namespace key. */
  @TableField("namespace_key")
  private String namespaceKey;

  /** Cursor type (DICT: ing_cursor_type). */
  @TableField("cursor_type_code")
  private String cursorTypeCode;

  /** Raw value before advancement. */
  @TableField("prev_value")
  private String prevValue;

  /** Raw value after advancement. */
  @TableField("new_value")
  private String newValue;

  /** Maximum observed value during advancement. */
  @TableField("observed_max_value")
  private String observedMaxValue;

  /** Normalized time before advancement. */
  @TableField("prev_instant")
  private Instant prevInstant;

  /** Normalized time after advancement. */
  @TableField("new_instant")
  private Instant newInstant;

  /** Normalized numeric value before advancement. */
  @TableField("prev_numeric")
  private BigDecimal prevNumeric;

  /** Normalized numeric value after advancement. */
  @TableField("new_numeric")
  private BigDecimal newNumeric;

  /** Covered window start (UTC, inclusive). */
  @TableField("window_from")
  private Instant windowFrom;

  /** Covered window end (UTC, exclusive). */
  @TableField("window_to")
  private Instant windowTo;

  /** Advancement direction (FORWARD/BACKFILL). */
  @TableField("direction_code")
  private String directionCode;

  /** Event idempotent key (UK: uk_cur_evt_idem). */
  @TableField("idempotent_key")
  private String idempotentKey;

  /** Most recent associated schedule instance. */
  @TableField("schedule_instance_id")
  private Long scheduleInstanceId;

  /** Most recent associated plan. */
  @TableField("plan_id")
  private Long planId;

  /** Most recent associated slice. */
  @TableField("slice_id")
  private Long sliceId;

  /** Most recent associated task. */
  @TableField("task_id")
  private Long taskId;

  /** Most recent associated run. */
  @TableField("run_id")
  private Long runId;

  /** Most recent associated batch. */
  @TableField("batch_id")
  private Long batchId;

  /** Expression hash used during advancement. */
  @TableField("expr_hash")
  private String exprHash;
}
