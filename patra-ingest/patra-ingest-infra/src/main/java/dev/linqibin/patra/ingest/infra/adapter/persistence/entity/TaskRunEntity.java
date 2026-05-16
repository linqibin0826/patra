package dev.linqibin.patra.ingest.infra.adapter.persistence.entity;

import dev.linqibin.starter.jpa.entity.ChildJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

/// 任务执行记录 JPA 实体，映射到表 `ing_task_run`。
///
/// 表结构: 表示任务的单次执行尝试，包含重试信息和运行快照。
///
/// 关键字段说明:
///
/// - `attempt_no` 每任务唯一（唯一约束: uk_task_run_attempt），追踪尝试序列
/// - `checkpoint`/`stats` 是 JSON 快照，用于恢复点和指标
/// - 时间字段追踪开始/结束，支持心跳超时检查
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
    name = "ing_task_run",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_task_run_attempt",
          columnNames = {"task_id", "attempt_no"})
    },
    indexes = {
      @Index(name = "idx_run_task", columnList = "task_id"),
      @Index(name = "idx_run_status", columnList = "status_code")
    })
public class TaskRunEntity extends ChildJpaEntity {

  /// 关联的任务 ID
  @Column(name = "task_id", nullable = false)
  private Long taskId;

  /// 尝试序号（从 1 开始）
  @Column(name = "attempt_no", nullable = false)
  private Integer attemptNo;

  /// 数据源代码（冗余）
  @Column(name = "provenance_code", length = 64)
  private String provenanceCode;

  /// 操作代码（冗余）
  @Column(name = "operation_code", length = 32)
  private String operationCode;

  /// 运行状态（字典: ing_task_run_status）
  @Column(name = "status_code", nullable = false, length = 32)
  private String statusCode;

  /// 运行检查点（JSON；如 nextToken/resumeHint）
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "checkpoint", columnDefinition = "JSON")
  private JsonNode checkpoint;

  /// 运行统计（JSON；如 fetched/upserted）
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "stats", columnDefinition = "JSON")
  private JsonNode stats;

  /// 错误原因（TEXT；必要时截断）
  @Column(name = "error", columnDefinition = "TEXT")
  private String error;

  /// 实际开始时间
  @Column(name = "started_at")
  private Instant startedAt;

  /// 结束时间
  @Column(name = "finished_at")
  private Instant finishedAt;

  /// 最后心跳时间
  @Column(name = "last_heartbeat")
  private Instant lastHeartbeat;

  /// 分布式追踪关联 ID
  @Column(name = "correlation_id", length = 64)
  private String correlationId;
}
