package dev.linqibin.patra.ingest.infra.adapter.persistence.entity;

import com.patra.starter.jpa.entity.SoftDeletableJpaEntity;
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
import tools.jackson.databind.JsonNode;

/// 采集任务 JPA 实体，映射到表 `ing_task`。
///
/// 表结构: 表示从计划切片派生的可执行任务，绑定到数据源、操作、幂等键和租约。
///
/// 关键字段说明:
///
/// - `idempotent_key` 全局唯一（唯一约束: uk_task_idem），防止重复任务
/// - `params` 存储规范化的任务参数
/// - 租约字段（`lease_owner`/`leased_until`/`lease_count`）支持抢占/续约模型
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
    name = "ing_task",
    indexes = {
      @Index(name = "uk_task_idem", columnList = "idempotent_key", unique = true),
      @Index(name = "idx_task_slice", columnList = "slice_id"),
      @Index(name = "idx_task_status", columnList = "status_code"),
      @Index(name = "idx_task_prov_op", columnList = "provenance_code, operation_code"),
      @Index(name = "idx_task_lease", columnList = "lease_owner, leased_until")
    })
public class TaskEntity extends SoftDeletableJpaEntity {

  /// 调度实例 ID（冗余）
  @Column(name = "schedule_instance_id")
  private Long scheduleInstanceId;

  /// 关联的计划 ID
  @Column(name = "plan_id", nullable = false)
  private Long planId;

  /// 关联的切片 ID
  @Column(name = "slice_id", nullable = false)
  private Long sliceId;

  /// 数据源代码（字典: ing_provenance）
  @Column(name = "provenance_code", nullable = false, length = 64)
  private String provenanceCode;

  /// 操作类型代码（字典: ing_operation）
  @Column(name = "operation_code", nullable = false, length = 32)
  private String operationCode;

  /// 任务参数（JSON；规范化持久化）
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "params", columnDefinition = "JSON")
  private JsonNode params;

  /// 幂等键（唯一约束: uk_task_idem）
  @Column(name = "idempotent_key", nullable = false, length = 128)
  private String idempotentKey;

  /// 执行表达式哈希
  @Column(name = "expr_hash", length = 64)
  private String exprHash;

  /// 调度优先级（1 高 → 9 低）
  @Column(name = "priority")
  private Integer priority;

  /// 租约拥有者
  @Column(name = "lease_owner", length = 128)
  private String leaseOwner;

  /// 租约过期时间（UTC）
  @Column(name = "leased_until")
  private Instant leasedUntil;

  /// 租约抢占/续约计数
  @Column(name = "lease_count")
  private Integer leaseCount;

  /// 执行期间心跳时间
  @Column(name = "last_heartbeat_at")
  private Instant lastHeartbeatAt;

  /// 重试次数
  @Column(name = "retry_count")
  private Integer retryCount;

  /// 最后错误代码
  @Column(name = "last_error_code", length = 64)
  private String lastErrorCode;

  /// 最后错误消息
  @Column(name = "last_error_msg", length = 512)
  private String lastErrorMsg;

  /// 任务状态（字典: ing_task_status）
  @Column(name = "status_code", nullable = false, length = 32)
  private String statusCode;

  /// 计划开始时间
  @Column(name = "scheduled_at")
  private Instant scheduledAt;

  /// 实际开始时间
  @Column(name = "started_at")
  private Instant startedAt;

  /// 结束时间
  @Column(name = "finished_at")
  private Instant finishedAt;

  /// 分布式追踪关联 ID
  @Column(name = "correlation_id", length = 64)
  private String correlationId;
}
