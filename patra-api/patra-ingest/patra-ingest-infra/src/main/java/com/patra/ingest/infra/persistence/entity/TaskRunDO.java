package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <b>Task run DO</b> — table: <code>ing_task_run</code>
 *
 * <p>Represents a single attempt of a Task, including retry information and run snapshots.
 *
 * <p>Notes:
 *
 * <ul>
 *   <li><code>attempt_no</code> unique per task (UK: uk_task_run_attempt) tracks attempt sequence.
 *   <li><code>checkpoint</code>/<code>stats</code> are JSON snapshots for resume points and
 *       metrics.
 *   <li>Time fields track start/finish and support heartbeat timeout checks.
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ing_task_run", autoResultMap = true)
public class TaskRunDO extends BaseDO {

  /** Associated task id. */
  @TableField("task_id")
  private Long taskId;

  /** Attempt sequence number (1-based). */
  @TableField("attempt_no")
  private Integer attemptNo;

  /** Provenance code (redundant). */
  @TableField("provenance_code")
  private String provenanceCode;

  /** Operation code (redundant). */
  @TableField("operation_code")
  private String operationCode;

  /** Run status (DICT: ing_task_run_status) TODO Consider using an enum for immutable values. */
  @TableField("status_code")
  private String statusCode;

  /** Run checkpoint (JSON; e.g., nextToken/resumeHint). */
  @TableField(value = "checkpoint", typeHandler = JacksonTypeHandler.class)
  private JsonNode checkpoint;

  /** Run statistics (JSON; e.g., fetched/upserted). */
  @TableField(value = "stats", typeHandler = JacksonTypeHandler.class)
  private JsonNode stats;

  /** Error reason (TEXT; truncated when necessary). */
  @TableField("error")
  private String error;

  /** Actual start time. */
  @TableField("started_at")
  private Instant startedAt;

  /** Finish time. */
  @TableField("finished_at")
  private Instant finishedAt;

  /** Last heartbeat time. */
  @TableField("last_heartbeat")
  private Instant lastHeartbeat;

  /** Scheduler run id (for tracing external execution record). */
  @TableField("scheduler_run_id")
  private String schedulerRunId;

  /** Trace / Correlation ID */
  @TableField("correlation_id")
  private String correlationId;
}
