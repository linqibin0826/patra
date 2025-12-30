package com.patra.ingest.infra.adapter.persistence.entity;

import com.fasterxml.jackson.databind.JsonNode;
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

/// 采集计划 JPA 实体，映射到表 `ing_plan`。
///
/// 表结构: 表示单个采集批次蓝图，捕获数据源配置、表达式原型和切片策略。
///
/// 关键字段说明:
///
/// - `plan_key` 是可读的幂等键（唯一约束: uk_plan_key）
/// - `expr_proto_snapshot` 和 `provenance_config_snapshot` 以 JSON 快照形式存储，用于重放和比较
/// - `slice_strategy_code` + `slice_params` 决定如何派生子切片
/// - `window_spec` 以 JSON 存储窗口边界；支持多种策略
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
    name = "ing_plan",
    indexes = {
      @Index(name = "uk_plan_key", columnList = "plan_key", unique = true),
      @Index(name = "idx_plan_sched", columnList = "schedule_instance_id"),
      @Index(name = "idx_plan_status", columnList = "status_code"),
      @Index(name = "idx_plan_prov_op", columnList = "provenance_code, operation_code")
    })
public class PlanEntity extends SoftDeletableJpaEntity {

  /// 关联的调度实例 ID
  @Column(name = "schedule_instance_id", nullable = false)
  private Long scheduleInstanceId;

  /// 外部幂等键（可读）
  @Column(name = "plan_key", nullable = false, length = 128)
  private String planKey;

  /// 数据源代码冗余（用于按数据源分组）
  @Column(name = "provenance_code", length = 64)
  private String provenanceCode;

  /// 操作类型代码（字典: ing_operation）
  @Column(name = "operation_code", nullable = false, length = 32)
  private String operationCode;

  /// 表达式原型哈希（规范化 AST 指纹）
  @Column(name = "expr_proto_hash", nullable = false, length = 64)
  private String exprProtoHash;

  /// 表达式原型快照（JSON AST，不含本地条件）
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "expr_proto_snapshot", columnDefinition = "JSON")
  private JsonNode exprProtoSnapshot;

  /// 数据源配置快照（JSON，运行时不变参数）
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "provenance_config_snapshot", columnDefinition = "JSON")
  private JsonNode provenanceConfigSnapshot;

  /// 数据源配置快照哈希（用于变更检测）
  @Column(name = "provenance_config_hash", length = 64)
  private String provenanceConfigHash;

  /// 切片策略代码（TIME/DATE/ID_RANGE/CURSOR 等）
  @Column(name = "slice_strategy_code", nullable = false, length = 32)
  private String sliceStrategyCode;

  /// 切片参数快照（JSON；策略特定细节）
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "slice_params", columnDefinition = "JSON")
  private JsonNode sliceParams;

  /// 窗口边界规格（JSON；模式因 slice_strategy_code 而异）
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "window_spec", columnDefinition = "JSON", nullable = false)
  private JsonNode windowSpec;

  /// TIME 策略的反规范化窗口起始时间戳（应用层维护）
  @Column(name = "window_from_ts")
  private Instant windowFromTs;

  /// TIME 策略的反规范化窗口结束时间戳（应用层维护）
  @Column(name = "window_to_ts")
  private Instant windowToTs;

  /// 状态代码（字典: ing_plan_status）
  @Column(name = "status_code", nullable = false, length = 32)
  private String statusCode;
}
