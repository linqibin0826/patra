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
 * 调度实例 DO - 对应 ing_schedule_instance 表。
 * 
 * <p>记录每次触发的根信息，用于追踪和审计。
 * 
 * @author linqibin
 * @since 0.1.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ing_schedule_instance", autoResultMap = true)
public class IngScheduleInstanceDO extends BaseDO {
    
    /** 调度器编码（如：XXL、QUARTZ） */
    @TableField("scheduler_code")
    private String schedulerCode;
    
    /** 调度器任务ID */
    @TableField("scheduler_job_id")
    private String schedulerJobId;
    
    /** 调度器日志ID */
    @TableField("scheduler_log_id")
    private String schedulerLogId;
    
    /** 触发类型编码（如：SCHEDULE、MANUAL） */
    @TableField("trigger_type_code")
    private String triggerTypeCode;
    
    /** 触发时间 */
    @TableField("triggered_at")
    private Instant triggeredAt;
    
    /** 触发参数（JSON格式） */
    @TableField(value = "trigger_params", typeHandler = JacksonTypeHandler.class)
    private JsonNode triggerParams;
    
    /** 数据源编码（冗余字段） */
    @TableField("provenance_code")
    private String provenanceCode;
    
    /** 状态编码 */
    @TableField("status_code")
    private String statusCode;
    
    /** 完成时间 */
    @TableField("completed_at")
    private Instant completedAt;
    
    /** 失败原因 */
    @TableField("failure_reason")
    private String failureReason;
    
    /** 备注信息（JSON格式） */
    @TableField(value = "record_remarks", typeHandler = JacksonTypeHandler.class)
    private JsonNode recordRemarks;
}
