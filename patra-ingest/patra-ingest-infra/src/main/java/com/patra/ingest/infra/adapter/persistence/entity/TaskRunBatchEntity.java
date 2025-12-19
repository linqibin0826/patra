package com.patra.ingest.infra.adapter.persistence.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.jpa.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/// 任务执行批次 JPA 实体，映射到表 `ing_task_run_batch`。
///
/// 表结构: 表示任务执行期间的批次核算（页面/令牌步骤）；恢复/去重的最小单元。
///
/// 关键字段说明:
///
/// - `idempotent_key` 唯一（唯一约束: uk_batch_idem），避免重试时重复批次
/// - `before_token`/`after_token` 捕获分页游标以支持回溯
/// - `stats` 以 JSON 存储批次级指标（fetched/upserted 等）
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
    name = "ing_task_run_batch",
    indexes = {
      @Index(name = "uk_batch_idem", columnList = "idempotent_key", unique = true),
      @Index(name = "idx_batch_run", columnList = "run_id"),
      @Index(name = "idx_batch_task", columnList = "task_id"),
      @Index(name = "idx_batch_status", columnList = "status_code")
    })
public class TaskRunBatchEntity extends BaseJpaEntity {

  /// 关联的运行 ID
  @Column(name = "run_id", nullable = false)
  private Long runId;

  /// 关联的任务 ID（冗余）
  @Column(name = "task_id", nullable = false)
  private Long taskId;

  /// 关联的切片 ID（冗余）
  @Column(name = "slice_id")
  private Long sliceId;

  /// 关联的计划 ID（冗余）
  @Column(name = "plan_id")
  private Long planId;

  /// 执行表达式哈希（冗余）
  @Column(name = "expr_hash", length = 64)
  private String exprHash;

  /// 数据源代码（冗余）
  @Column(name = "provenance_code", length = 64)
  private String provenanceCode;

  /// 操作代码（冗余）
  @Column(name = "operation_code", length = 32)
  private String operationCode;

  /// 批次序号（从 1 开始，顺序递增）
  @Column(name = "batch_no", nullable = false)
  private Integer batchNo;

  /// 页码（基于令牌分页时为 null）
  @Column(name = "page_no")
  private Integer pageNo;

  /// 页大小
  @Column(name = "page_size")
  private Integer pageSize;

  /// 批次开始前的令牌
  @Column(name = "before_token", length = 512)
  private String beforeToken;

  /// 批次结束后的令牌
  @Column(name = "after_token", length = 512)
  private String afterToken;

  /// 批次幂等键（唯一约束: uk_batch_idem）
  @Column(name = "idempotent_key", nullable = false, length = 128)
  private String idempotentKey;

  /// 记录数
  @Column(name = "record_count")
  private Integer recordCount;

  /// 批次状态（字典: ing_batch_status）
  @Column(name = "status_code", nullable = false, length = 32)
  private String statusCode;

  /// 批次提交时间
  @Column(name = "committed_at")
  private Instant committedAt;

  /// 失败原因（TEXT）
  @Column(name = "error", columnDefinition = "TEXT")
  private String error;

  /// 批次载荷的对象存储引用
  @Column(name = "storage_key", length = 256)
  private String storageKey;

  /// 批次级统计（JSON）
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "stats", columnDefinition = "JSON")
  private JsonNode stats;
}
