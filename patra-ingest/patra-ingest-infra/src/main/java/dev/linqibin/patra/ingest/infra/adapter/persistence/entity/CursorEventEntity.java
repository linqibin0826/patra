package dev.linqibin.patra.ingest.infra.adapter.persistence.entity;

import com.patra.starter.jpa.entity.ValueObjectJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/// 游标推进事件 JPA 实体，映射到表 `ing_cursor_event`。
///
/// 表结构: 仅追加审计日志，记录从旧游标值到新游标值的每次推进，支持重放和端到端追踪。
///
/// 关键字段说明:
///
/// - `idempotent_key` 具有唯一约束（UK: uk_cur_evt_idem）以防止重复写入
/// - 同时持久化时间/数值规范化值（prev/new + observed），用于跨类型排序和统计
/// - 冗余血缘字段链（schedule → plan → slice → task → run → batch）用于故障排查
/// - `direction_code` 标记回填（BACKFILL）与前向（FORWARD）推进
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
    name = "ing_cursor_event",
    indexes = {
      @Index(name = "uk_cur_evt_idem", columnList = "idempotent_key", unique = true),
      @Index(
          name = "idx_cur_evt_cursor",
          columnList = "provenance_code, operation_code, cursor_key"),
      @Index(name = "idx_cur_evt_task", columnList = "task_id"),
      @Index(name = "idx_cur_evt_time", columnList = "new_instant")
    })
public class CursorEventEntity extends ValueObjectJpaEntity {

  /// 数据源代码
  @Column(name = "provenance_code", nullable = false, length = 64)
  private String provenanceCode;

  /// 操作类型代码
  @Column(name = "operation_code", nullable = false, length = 32)
  private String operationCode;

  /// 游标逻辑键
  @Column(name = "cursor_key", nullable = false, length = 64)
  private String cursorKey;

  /// 命名空间范围代码
  @Column(name = "namespace_scope_code", nullable = false, length = 32)
  private String namespaceScopeCode;

  /// 命名空间键
  @Column(name = "namespace_key", nullable = false, length = 64)
  private String namespaceKey;

  /// 游标类型代码（字典: ing_cursor_type）
  @Column(name = "cursor_type_code", nullable = false, length = 32)
  private String cursorTypeCode;

  /// 推进前的原始值
  @Column(name = "prev_value", length = 256)
  private String prevValue;

  /// 推进后的原始值
  @Column(name = "new_value", length = 256)
  private String newValue;

  /// 推进期间观察到的最大值
  @Column(name = "observed_max_value", length = 256)
  private String observedMaxValue;

  /// 推进前的规范化时间
  @Column(name = "prev_instant")
  private Instant prevInstant;

  /// 推进后的规范化时间
  @Column(name = "new_instant")
  private Instant newInstant;

  /// 推进前的规范化数值
  @Column(name = "prev_numeric", precision = 38, scale = 0)
  private BigDecimal prevNumeric;

  /// 推进后的规范化数值
  @Column(name = "new_numeric", precision = 38, scale = 0)
  private BigDecimal newNumeric;

  /// 覆盖窗口起始时间（UTC，含）
  @Column(name = "window_from")
  private Instant windowFrom;

  /// 覆盖窗口结束时间（UTC，不含）
  @Column(name = "window_to")
  private Instant windowTo;

  /// 推进方向代码（FORWARD/BACKFILL）
  @Column(name = "direction_code", nullable = false, length = 32)
  private String directionCode;

  /// 事件幂等键（唯一约束: uk_cur_evt_idem）
  @Column(name = "idempotent_key", nullable = false, length = 128)
  private String idempotentKey;

  /// 关联的最近调度实例 ID
  @Column(name = "schedule_instance_id")
  private Long scheduleInstanceId;

  /// 关联的最近计划 ID
  @Column(name = "plan_id")
  private Long planId;

  /// 关联的最近切片 ID
  @Column(name = "slice_id")
  private Long sliceId;

  /// 关联的最近任务 ID
  @Column(name = "task_id")
  private Long taskId;

  /// 关联的最近运行 ID
  @Column(name = "run_id")
  private Long runId;

  /// 关联的最近批次 ID
  @Column(name = "batch_id")
  private Long batchId;

  /// 推进时使用的表达式哈希
  @Column(name = "expr_hash", length = 64)
  private String exprHash;
}
