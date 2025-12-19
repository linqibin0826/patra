package com.patra.ingest.infra.adapter.persistence.entity;

import com.patra.starter.jpa.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/// 游标 JPA 实体，映射到表 `ing_cursor`。
///
/// 表结构: 维护数据源 + 操作 + 命名空间的当前水位，支持三种游标类型: time / numeric / token。
///
/// 关键字段说明:
///
/// - `namespace_scope_code` + `namespace_key` 区分命名空间（GLOBAL / EXPR / CUSTOM）
/// - `normalized_instant`/`normalized_numeric` 规范化值以支持排序和范围查询
/// - 冗余血缘字段（schedule/plan/slice/task/run/batch）支持快速回溯
/// - 推荐唯一键: (provenance_code, operation_code, cursor_key, namespace_scope_code, namespace_key)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
    name = "ing_cursor",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_cursor_composite",
          columnNames = {
            "provenance_code",
            "operation_code",
            "cursor_key",
            "namespace_scope_code",
            "namespace_key"
          })
    },
    indexes = {
      @Index(name = "idx_cursor_prov_op", columnList = "provenance_code, operation_code"),
      @Index(name = "idx_cursor_normalized", columnList = "normalized_instant, normalized_numeric")
    })
public class CursorEntity extends BaseJpaEntity {

  /// 数据源代码（与注册表一致）
  @Column(name = "provenance_code", nullable = false, length = 64)
  private String provenanceCode;

  /// 操作类型代码
  @Column(name = "operation_code", nullable = false, length = 32)
  private String operationCode;

  /// 游标键（如 updated_at / seq_id / cursor_token）
  @Column(name = "cursor_key", nullable = false, length = 64)
  private String cursorKey;

  /// 命名空间范围（GLOBAL/EXPR/CUSTOM）
  @Column(name = "namespace_scope_code", nullable = false, length = 32)
  private String namespaceScopeCode;

  /// 命名空间键（expr_hash 或自定义哈希）
  @Column(name = "namespace_key", nullable = false, length = 64)
  private String namespaceKey;

  /// 游标类型（字典: ing_cursor_type）
  @Column(name = "cursor_type_code", nullable = false, length = 32)
  private String cursorTypeCode;

  /// 当前原始游标值
  @Column(name = "cursor_value", length = 256)
  private String cursorValue;

  /// 观察到的最大值
  @Column(name = "observed_max_value", length = 256)
  private String observedMaxValue;

  /// 规范化时间值（当类型=TIME）
  @Column(name = "normalized_instant")
  private Instant normalizedInstant;

  /// 规范化数值（当类型=ID）
  @Column(name = "normalized_numeric", precision = 38, scale = 0)
  private BigDecimal normalizedNumeric;

  /// 最近推进此游标的调度实例 ID
  @Column(name = "schedule_instance_id")
  private Long scheduleInstanceId;

  /// 最近关联的计划 ID
  @Column(name = "plan_id")
  private Long planId;

  /// 最近关联的切片 ID
  @Column(name = "slice_id")
  private Long sliceId;

  /// 最近关联的任务 ID
  @Column(name = "task_id")
  private Long taskId;

  /// 最近关联的运行 ID
  @Column(name = "last_run_id")
  private Long lastRunId;

  /// 最近关联的批次 ID
  @Column(name = "last_batch_id")
  private Long lastBatchId;

  /// 最近推进时使用的表达式哈希
  @Column(name = "expr_hash", length = 64)
  private String exprHash;
}
