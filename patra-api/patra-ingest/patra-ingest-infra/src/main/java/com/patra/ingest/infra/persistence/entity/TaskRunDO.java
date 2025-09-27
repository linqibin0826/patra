package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

/**
 * <p><b>任务运行 DO</b> —— 映射表：<code>ing_task_run</code></p>
 * <p>语义：描述一次 Task 的具体尝试（attempt），包含重试信息与运行快照。</p>
 * <p>要点：
 * <ul>
 *   <li><code>attempt_no</code> 在同一任务内唯一，记录第几次尝试。</li>
 *   <li><code>checkpoint</code>/<code>stats</code> 使用 JSON 存储断点与统计指标。</li>
 *   <li>时间字段追踪开始/结束节点并支持心跳超时判定。</li>
 * </ul>
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ing_task_run", autoResultMap = true)
public class TaskRunDO extends BaseDO {

    /** 关联任务 ID */
    @TableField("task_id")
    private Long taskId;

    /** 第几次尝试（1 起） */
    @TableField("attempt_no")
    private Integer attemptNo;

    /** 来源代码冗余 */
    @TableField("provenance_code")
    private String provenanceCode;

    /** 操作类型冗余 */
    @TableField("operation_code")
    private String operationCode;

    /** 运行状态（DICT：ing_task_run_status） */
    @TableField("status_code")
    private String statusCode;

    /** 运行检查点（JSON，例如 nextToken / resumeHint） */
    @TableField(value = "checkpoint", typeHandler = JacksonTypeHandler.class)
    private JsonNode checkpoint;

    /** 统计指标（JSON，如 fetched/upserted 等） */
    @TableField(value = "stats", typeHandler = JacksonTypeHandler.class)
    private JsonNode stats;

    /** 失败原因（TEXT，必要时截断） */
    @TableField("error")
    private String error;

    /** 窗口起点（UTC，含） */
    @TableField("window_from")
    private Instant windowFrom;

    /** 窗口终点（UTC，不含） */
    @TableField("window_to")
    private Instant windowTo;

    /** 实际开始时间 */
    @TableField("started_at")
    private Instant startedAt;

    /** 完成时间 */
    @TableField("finished_at")
    private Instant finishedAt;

    /** 最近心跳 */
    @TableField("last_heartbeat")
    private Instant lastHeartbeat;

    /** 调度器运行 ID（用于回溯外部执行记录） */
    @TableField("scheduler_run_id")
    private String schedulerRunId;

    /** Trace / Correlation ID */
    @TableField("correlation_id")
    private String correlationId;
}
