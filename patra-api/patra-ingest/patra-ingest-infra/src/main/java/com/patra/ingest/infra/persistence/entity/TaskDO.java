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
 * <p><b>采集任务 DO</b> —— 映射表：<code>ing_task</code></p>
 * <p>语义：每个计划切片派生出的执行任务，绑定来源、操作、幂等键与租约。</p>
 * <p>要点：
 * <ul>
 *   <li><code>idempotent_key</code> 全局唯一，保障重复调度不会生成重复任务。</li>
 *   <li><code>params</code> 保存规范化任务参数，配合 {@link JacksonTypeHandler} 保留结构。</li>
 *   <li>租约字段（<code>lease_owner</code>/<code>leased_until</code>/<code>lease_count</code>）支撑抢占与续租模型。</li>
 * </ul>
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ing_task", autoResultMap = true)
public class TaskDO extends BaseDO {

    /** 调度实例 ID（冗余） */
    @TableField("schedule_instance_id")
    private Long scheduleInstanceId;

    /** 关联计划 ID */
    @TableField("plan_id")
    private Long planId;

    /** 关联切片 ID */
    @TableField("slice_id")
    private Long sliceId;

    /** 来源代码（DICT：ing_provenance） */
    @TableField("provenance_code")
    private String provenanceCode;

    /** 操作类型编码（DICT：ing_operation） */
    @TableField("operation_code")
    private String operationCode;

    /** 任务参数（JSON，规范化后持久化） */
    @TableField(value = "params", typeHandler = JacksonTypeHandler.class)
    private JsonNode params;

    /** 幂等键（UK：uk_task_idem） */
    @TableField("idempotent_key")
    private String idempotentKey;

    /** 执行表达式哈希 */
    @TableField("expr_hash")
    private String exprHash;

    /** 调度优先级（1高→9低） */
    @TableField("priority")
    private Integer priority;

    /** 租约持有者 */
    @TableField("lease_owner")
    private String leaseOwner;

    /** 租约到期时间（UTC） */
    @TableField("leased_until")
    private Instant leasedUntil;

    /** 租约抢占 / 续租次数 */
    @TableField("lease_count")
    private Integer leaseCount;

    /** 执行期心跳时间 */
    @TableField("last_heartbeat_at")
    private Instant lastHeartbeatAt;

    /** 重试次数 */
    @TableField("retry_count")
    private Integer retryCount;

    /** 最近错误码 */
    @TableField("last_error_code")
    private String lastErrorCode;

    /** 最近错误信息 */
    @TableField("last_error_msg")
    private String lastErrorMsg;

    /** 任务状态（DICT：ing_task_status） */
    @TableField("status_code")
    private String statusCode;

    /** 计划开始时间 */
    @TableField("scheduled_at")
    private Instant scheduledAt;

    /** 实际开始时间 */
    @TableField("started_at")
    private Instant startedAt;

    /** 结束时间 */
    @TableField("finished_at")
    private Instant finishedAt;

    /** 调度器运行 ID（逐片触发时使用） */
    @TableField("scheduler_run_id")
    private String schedulerRunId;

    /** Trace / Correlation ID */
    @TableField("correlation_id")
    private String correlationId;
}
