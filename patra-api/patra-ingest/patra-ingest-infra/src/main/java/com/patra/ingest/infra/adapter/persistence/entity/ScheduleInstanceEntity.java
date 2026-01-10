package com.patra.ingest.infra.adapter.persistence.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.jpa.entity.ValueObjectJpaEntity;
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

/// 调度实例 JPA 实体，映射到表 `ing_schedule_instance`。
///
/// 表结构: 捕获外部调度触发的根上下文，支持运行时参数重放。
///
/// 关键字段说明:
///
/// - `scheduler_code`/`scheduler_job_id`/`scheduler_log_id` 追踪调度器来源和运行日志
/// - `trigger_params` 存储规范化的输入快照（JSON），用于跨语言重放；空映射不持久化
/// - `provenance_code` 匹配注册表；无物理外键，仅逻辑校验
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
    name = "ing_schedule_instance",
    indexes = {
      @Index(name = "idx_sched_inst_prov", columnList = "provenance_code"),
      @Index(name = "idx_sched_inst_time", columnList = "triggered_at"),
      @Index(name = "idx_sched_inst_job", columnList = "scheduler_code, scheduler_job_id")
    })
public class ScheduleInstanceEntity extends ValueObjectJpaEntity {

  /// 调度器来源（如 XXL、CRON、MANUAL）
  @Column(name = "scheduler_code", nullable = false, length = 32)
  private String schedulerCode;

  /// 调度器内部作业 ID（如 XXL jobId）
  @Column(name = "scheduler_job_id", length = 64)
  private String schedulerJobId;

  /// 调度器运行/日志 ID（如 XXL logId）
  @Column(name = "scheduler_log_id", length = 64)
  private String schedulerLogId;

  /// 触发类型代码（字典: ing_trigger_type）
  @Column(name = "trigger_type_code", nullable = false, length = 32)
  private String triggerTypeCode;

  /// 触发时间（UTC）
  @Column(name = "triggered_at", nullable = false)
  private Instant triggeredAt;

  /// 触发参数快照（JSON）
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "trigger_params", columnDefinition = "JSON")
  private JsonNode triggerParams;

  /// 目标数据源代码（如 PUBMED/EPMC）
  @Column(name = "provenance_code", nullable = false, length = 64)
  private String provenanceCode;
}
