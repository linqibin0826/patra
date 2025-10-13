package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <b>Task run batch DO</b> — table: <code>ing_task_run_batch</code>
 *
 * <p>Represents per-batch accounting (page/token steps) during a Task Run; the smallest unit for
 * resume/dedup.
 *
 * <p>Notes:
 *
 * <ul>
 *   <li><code>idempotent_key</code> unique (UK: uk_batch_idem) to avoid duplicate batches on
 *       retries.
 *   <li><code>before_token</code>/<code>after_token</code> capture pagination cursors for
 *       backtracking.
 *   <li><code>stats</code> stores batch-level metrics (fetched/upserted, etc.) in JSON.
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ing_task_run_batch", autoResultMap = true)
public class TaskRunBatchDO extends BaseDO {

  /** Associated run id. */
  @TableField("run_id")
  private Long runId;

  /** Associated task id (redundant). */
  @TableField("task_id")
  private Long taskId;

  /** Associated slice id (redundant). */
  @TableField("slice_id")
  private Long sliceId;

  /** Associated plan id (redundant). */
  @TableField("plan_id")
  private Long planId;

  /** Execution expression hash (redundant). */
  @TableField("expr_hash")
  private String exprHash;

  /** Provenance code (redundant). */
  @TableField("provenance_code")
  private String provenanceCode;

  /** Operation code (redundant). */
  @TableField("operation_code")
  private String operationCode;

  /** Batch number (1-based, sequential). */
  @TableField("batch_no")
  private Integer batchNo;

  /** Page number (null for token-based pagination). */
  @TableField("page_no")
  private Integer pageNo;

  /** Page size. */
  @TableField("page_size")
  private Integer pageSize;

  /** Token before batch start. */
  @TableField("before_token")
  private String beforeToken;

  /** Token after batch end. */
  @TableField("after_token")
  private String afterToken;

  /** Batch idempotent key (UK: uk_batch_idem). */
  @TableField("idempotent_key")
  private String idempotentKey;

  /** Record count. */
  @TableField("record_count")
  private Integer recordCount;

  /** Batch status (DICT: ing_batch_status). */
  @TableField("status_code")
  private String statusCode;

  /** Batch commit time. */
  @TableField("committed_at")
  private Instant committedAt;

  /** Failure reason (TEXT). */
  @TableField("error")
  private String error;

  /** Batch-level statistics (JSON). */
  @TableField(value = "stats", typeHandler = JacksonTypeHandler.class)
  private JsonNode stats;
}
