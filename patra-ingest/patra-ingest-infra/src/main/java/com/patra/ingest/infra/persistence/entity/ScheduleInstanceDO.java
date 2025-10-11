package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

/**
 * <p><b>Schedule instance DO</b> — table: <code>ing_schedule_instance</code></p>
 * <p>Captures the root context for an external scheduling trigger and enables replay of runtime parameters.</p>
 * <p>Notes:
 * <ul>
 *   <li><code>scheduler_code</code>/<code>scheduler_job_id</code>/<code>scheduler_log_id</code> track scheduler source and run logs.</li>
 *   <li><code>trigger_params</code> stores normalized input snapshot (JSON) for cross-language replay; empty map is not persisted.</li>
 *   <li><code>provenance_code</code> matches the registry; no physical FK, logical validation only.</li>
 * </ul>
 * </p>
 * <p>Audit fields come from {@link BaseDO BaseDO}.</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ing_schedule_instance", autoResultMap = true)
public class ScheduleInstanceDO extends BaseDO {

    /** Scheduler source (e.g., XXL, CRON, MANUAL). */
    @TableField("scheduler_code")
    private String schedulerCode;

    /** Scheduler internal job id (e.g., XXL jobId). */
    @TableField("scheduler_job_id")
    private String schedulerJobId;

    /** Scheduler run/log id (e.g., XXL logId). */
    @TableField("scheduler_log_id")
    private String schedulerLogId;

    /** Trigger type code (DICT: ing_trigger_type). */
    @TableField("trigger_type_code")
    private String triggerTypeCode;

    /** Trigger time (UTC). */
    @TableField("triggered_at")
    private Instant triggeredAt;

    /** Trigger parameters snapshot (JSON). */
    @TableField(value = "trigger_params", typeHandler = JacksonTypeHandler.class)
    private JsonNode triggerParams;

    /** Target provenance code (e.g., PUBMED/EPMC). */
    @TableField("provenance_code")
    private String provenanceCode;
}
