package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// 调度实例数据库实体,映射到表 `ing_schedule_instance`。
///
/// 表结构: 捕获外部调度触发的根上下文,支持运行时参数重放。
///
/// 关键字段说明:
///
/// - `scheduler_code`/`scheduler_job_id`/`scheduler_log_id` 追踪调度器来源和运行日志
///   - `trigger_params` 存储规范化的输入快照(JSON),用于跨语言重放;空映射不持久化
///   - `provenance_code` 匹配注册表;无物理外键,仅逻辑校验
///
/// 审计字段来自 {@link BaseDO}。
///
/// @author linqibin
/// @since 0.1.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ing_schedule_instance", autoResultMap = true)
public class ScheduleInstanceDO extends BaseDO {

  /// Scheduler source (e.g., XXL, CRON, MANUAL).
  @TableField("scheduler_code")
  private String schedulerCode;

  /// Scheduler internal job id (e.g., XXL jobId).
  @TableField("scheduler_job_id")
  private String schedulerJobId;

  /// Scheduler run/log id (e.g., XXL logId).
  @TableField("scheduler_log_id")
  private String schedulerLogId;

  /// Trigger type code (DICT: ing_trigger_type).
  @TableField("trigger_type_code")
  private String triggerTypeCode;

  /// Trigger time (UTC).
  @TableField("triggered_at")
  private Instant triggeredAt;

  /// Trigger parameters snapshot (JSON).
  @TableField(value = "trigger_params", typeHandler = JacksonTypeHandler.class)
  private JsonNode triggerParams;

  /// Target provenance code (e.g., PUBMED/EPMC).
  @TableField("provenance_code")
  private String provenanceCode;
}
