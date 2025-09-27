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
 * <p><b>调度实例 DO</b> —— 映射表：<code>ing_schedule_instance</code></p>
 * <p>语义：记录一次外部调度触发事件的“根”上下文，用于回放当时的运行参数。</p>
 * <p>要点：
 * <ul>
 *   <li><code>scheduler_code</code>/<code>scheduler_job_id</code>/<code>scheduler_log_id</code> 组合追踪调度来源与执行日志。</li>
 *   <li><code>trigger_params</code> 保存规范化的入参快照（JSON），便于跨语言重放。</li>
 *   <li><code>provenance_code</code> 与注册中心保持一致，不建物理外键，仅做逻辑校验。</li>
 * </ul>
 * </p>
 * <p>审计字段继承自 {@link com.patra.starter.mybatis.entity.BaseDO.BaseDO BaseDO}。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ing_schedule_instance", autoResultMap = true)
public class ScheduleInstanceDO extends BaseDO {

    /** 调度器来源（如 XXL、CRON、MANUAL） */
    @TableField("scheduler_code")
    private String schedulerCode;

    /** 调度器内部任务 ID（如 XXL 的 jobId） */
    @TableField("scheduler_job_id")
    private String schedulerJobId;

    /** 调度器运行/日志 ID（如 XXL 的 logId） */
    @TableField("scheduler_log_id")
    private String schedulerLogId;

    /** 触发类型编码（DICT：ing_trigger_type） */
    @TableField("trigger_type_code")
    private String triggerTypeCode;

    /** 触发时间（UTC） */
    @TableField("triggered_at")
    private Instant triggeredAt;

    /** 触发参数快照（JSON） */
    @TableField(value = "trigger_params", typeHandler = JacksonTypeHandler.class)
    private JsonNode triggerParams;

    /** 调度针对的来源代码（如 PUBMED/EPMC） */
    @TableField("provenance_code")
    private String provenanceCode;
}
