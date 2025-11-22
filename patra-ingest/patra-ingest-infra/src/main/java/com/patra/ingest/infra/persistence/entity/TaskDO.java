package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// 采集任务数据库实体,映射到表 `ing_task`。
///
/// 表结构: 表示从计划切片派生的可执行任务,绑定到数据源、操作、幂等键和租约。
///
/// 关键字段说明:
///
/// - `idempotent_key` 全局唯一(唯一约束: uk_task_idem),防止重复任务
///   - `params` 存储规范化的任务参数;通过 {@link JacksonTypeHandler} 持久化
///   - 租约字段(`lease_owner`/`leased_until`/`lease_count`) 支持抢占/续约模型
///
/// @author linqibin
/// @since 0.1.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ing_task", autoResultMap = true)
public class TaskDO extends BaseDO {

  /// Schedule instance ID (redundant).
  @TableField("schedule_instance_id")
  private Long scheduleInstanceId;

  /// Associated plan ID.
  @TableField("plan_id")
  private Long planId;

  /// Associated slice ID.
  @TableField("slice_id")
  private Long sliceId;

  /// Provenance code (DICT: ing_provenance).
  @TableField("provenance_code")
  private String provenanceCode;

  /// Operation type code (DICT: ing_operation).
  @TableField("operation_code")
  private String operationCode;

  /// Task parameters (JSON; normalized and persisted).
  @TableField(value = "params", typeHandler = JacksonTypeHandler.class)
  private JsonNode params;

  /// Idempotent key (UK: uk_task_idem).
  @TableField("idempotent_key")
  private String idempotentKey;

  /// Execution expression hash.
  @TableField("expr_hash")
  private String exprHash;

  /// Scheduling priority (1 high → 9 low).
  @TableField("priority")
  private Integer priority;

  /// Lease owner.
  @TableField("lease_owner")
  private String leaseOwner;

  /// Lease expiration time (UTC).
  @TableField("leased_until")
  private Instant leasedUntil;

  /// Lease preemption/renewal count.
  @TableField("lease_count")
  private Integer leaseCount;

  /// Heartbeat time during execution.
  @TableField("last_heartbeat_at")
  private Instant lastHeartbeatAt;

  /// Retry count.
  @TableField("retry_count")
  private Integer retryCount;

  /// Last error code.
  @TableField("last_error_code")
  private String lastErrorCode;

  /// Last error message.
  @TableField("last_error_msg")
  private String lastErrorMsg;

  /// Task status (DICT: ing_task_status).
  @TableField("status_code")
  private String statusCode;

  /// Scheduled start time.
  @TableField("scheduled_at")
  private Instant scheduledAt;

  /// Actual start time.
  @TableField("started_at")
  private Instant startedAt;

  /// Finish time.
  @TableField("finished_at")
  private Instant finishedAt;

  /// Trace / Correlation ID for distributed tracing
  @TableField("correlation_id")
  private String correlationId;
}
