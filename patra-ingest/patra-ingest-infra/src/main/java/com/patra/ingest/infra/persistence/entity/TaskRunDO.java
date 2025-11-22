package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// 任务执行记录数据库实体,映射到表 `ing_task_run`。
///
/// 表结构: 表示任务的单次执行尝试,包含重试信息和运行快照。
///
/// 关键字段说明:
///
/// - `attempt_no` 每任务唯一(唯一约束: uk_task_run_attempt),追踪尝试序列
///   - `checkpoint`/`stats` 是 JSON 快照,用于恢复点和指标
///   - 时间字段追踪开始/结束,支持心跳超时检查
///
/// @author linqibin
/// @since 0.1.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ing_task_run", autoResultMap = true)
public class TaskRunDO extends BaseDO {

  /// Associated task id.
  @TableField("task_id")
  private Long taskId;

  /// Attempt sequence number (1-based).
  @TableField("attempt_no")
  private Integer attemptNo;

  /// Provenance code (redundant).
  @TableField("provenance_code")
  private String provenanceCode;

  /// Operation code (redundant).
  @TableField("operation_code")
  private String operationCode;

  /// Run status (DICT: ing_task_run_status) TODO Consider using an enum for immutable values.
  @TableField("status_code")
  private String statusCode;

  /// Run checkpoint (JSON; e.g., nextToken/resumeHint).
  @TableField(value = "checkpoint", typeHandler = JacksonTypeHandler.class)
  private JsonNode checkpoint;

  /// Run statistics (JSON; e.g., fetched/upserted).
  @TableField(value = "stats", typeHandler = JacksonTypeHandler.class)
  private JsonNode stats;

  /// Error reason (TEXT; truncated when necessary).
  @TableField("error")
  private String error;

  /// Actual start time.
  @TableField("started_at")
  private Instant startedAt;

  /// Finish time.
  @TableField("finished_at")
  private Instant finishedAt;

  /// Last heartbeat time.
  @TableField("last_heartbeat")
  private Instant lastHeartbeat;

  /// Trace / Correlation ID for distributed tracing
  @TableField("correlation_id")
  private String correlationId;
}
