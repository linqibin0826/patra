package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// 任务执行批次数据库实体,映射到表 `ing_task_run_batch`。
///
/// 表结构: 表示任务执行期间的批次核算(页面/令牌步骤);恢复/去重的最小单元。
///
/// 关键字段说明:
///
/// - `idempotent_key` 唯一(唯一约束: uk_batch_idem),避免重试时重复批次
///   - `before_token`/`after_token` 捕获分页游标以支持回溯
///   - `stats` 以 JSON 存储批次级指标(fetched/upserted 等)
///
/// @author linqibin
/// @since 0.1.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ing_task_run_batch", autoResultMap = true)
public class TaskRunBatchDO extends BaseDO {

  /// Associated run id.
  @TableField("run_id")
  private Long runId;

  /// Associated task id (redundant).
  @TableField("task_id")
  private Long taskId;

  /// Associated slice id (redundant).
  @TableField("slice_id")
  private Long sliceId;

  /// Associated plan id (redundant).
  @TableField("plan_id")
  private Long planId;

  /// Execution expression hash (redundant).
  @TableField("expr_hash")
  private String exprHash;

  /// Provenance code (redundant).
  @TableField("provenance_code")
  private String provenanceCode;

  /// Operation code (redundant).
  @TableField("operation_code")
  private String operationCode;

  /// Batch number (1-based, sequential).
  @TableField("batch_no")
  private Integer batchNo;

  /// Page number (null for token-based pagination).
  @TableField("page_no")
  private Integer pageNo;

  /// Page size.
  @TableField("page_size")
  private Integer pageSize;

  /// Token before batch start.
  @TableField("before_token")
  private String beforeToken;

  /// Token after batch end.
  @TableField("after_token")
  private String afterToken;

  /// Batch idempotent key (UK: uk_batch_idem).
  @TableField("idempotent_key")
  private String idempotentKey;

  /// Record count.
  @TableField("record_count")
  private Integer recordCount;

  /// Batch status (DICT: ing_batch_status).
  @TableField("status_code")
  private String statusCode;

  /// Batch commit time.
  @TableField("committed_at")
  private Instant committedAt;

  /// Failure reason (TEXT).
  @TableField("error")
  private String error;

  /// Object storage reference for batch payload.
  @TableField("storage_key")
  private String storageKey;

  /// Batch-level statistics (JSON).
  @TableField(value = "stats", typeHandler = JacksonTypeHandler.class)
  private JsonNode stats;
}
