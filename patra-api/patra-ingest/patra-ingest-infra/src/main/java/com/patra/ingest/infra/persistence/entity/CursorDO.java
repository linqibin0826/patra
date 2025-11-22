package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// 游标数据库实体,映射到表 `ing_cursor`。
/// 
/// 表结构: 维护数据源 + 操作 + 命名空间的当前水位,支持三种游标类型: time / numeric / token。
/// 
/// 关键字段说明:
/// 
/// - `namespace_scope_code` + `namespace_key` 区分命名空间(GLOBAL / EXPR / CUSTOM)
///   - `normalized_instant`/`normalized_numeric` 规范化值以支持排序和范围查询
///   - 冗余血缘字段(schedule/plan/slice/task/run/batch) 支持快速回溯
///   - 推荐唯一键: (provenance_code, operation_code, cursor_key, namespace_scope_code, namespace_key)
/// 
/// @author linqibin
/// @since 0.1.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ing_cursor")
public class CursorDO extends BaseDO {

  /// Provenance code (consistent with registry).
  @TableField("provenance_code")
  private String provenanceCode;

  /// Operation type code.
  @TableField("operation_code")
  private String operationCode;

  /// Cursor key (e.g., updated_at / seq_id / cursor_token).
  @TableField("cursor_key")
  private String cursorKey;

  /// Namespace scope (GLOBAL/EXPR/CUSTOM).
  @TableField("namespace_scope_code")
  private String namespaceScopeCode;

  /// Namespace key (expr_hash or a custom hash).
  @TableField("namespace_key")
  private String namespaceKey;

  /// Cursor type (DICT: ing_cursor_type).
  @TableField("cursor_type_code")
  private String cursorTypeCode;

  /// Current raw cursor value.
  @TableField("cursor_value")
  private String cursorValue;

  /// Maximum observed value.
  @TableField("observed_max_value")
  private String observedMaxValue;

  /// Normalized time value (when type=TIME).
  @TableField("normalized_instant")
  private Instant normalizedInstant;

  /// Normalized numeric value (when type=ID).
  @TableField("normalized_numeric")
  private BigDecimal normalizedNumeric;

  /// Most recent schedule instance advancing this cursor.
  @TableField("schedule_instance_id")
  private Long scheduleInstanceId;

  /// Most recent associated plan.
  @TableField("plan_id")
  private Long planId;

  /// Most recent associated slice.
  @TableField("slice_id")
  private Long sliceId;

  /// Most recent associated task.
  @TableField("task_id")
  private Long taskId;

  /// Most recent associated run.
  @TableField("last_run_id")
  private Long lastRunId;

  /// Most recent associated batch.
  @TableField("last_batch_id")
  private Long lastBatchId;

  /// Expression hash used in the latest advancement.
  @TableField("expr_hash")
  private String exprHash;
}
