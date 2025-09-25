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
 * 任务 DO - 对应 ing_task 表。
 * 
 * <p>存储任务信息，包含执行参数和调度属性。
 * 
 * @author linqibin
 * @since 0.1.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ing_task", autoResultMap = true)
public class IngTaskDO extends BaseDO {
    
    /** 调度实例ID（冗余字段） */
    @TableField("schedule_instance_id")
    private Long scheduleInstanceId;
    
    /** 计划ID（冗余字段） */
    @TableField("plan_id")
    private Long planId;
    
    /** 切片ID */
    @TableField("slice_id")
    private Long sliceId;
    
    /** 数据源编码（冗余字段） */
    @TableField("provenance_code")
    private String provenanceCode;
    
    /** 操作类型编码（冗余字段） */
    @TableField("operation_code")
    private String operationCode;
    
    /** 任务参数（JSON格式） */
    @TableField(value = "params", typeHandler = JacksonTypeHandler.class)
    private JsonNode params;
    
    /** 表达式哈希（冗余字段） */
    @TableField("expr_hash")
    private String exprHash;
    
    /** 幂等键 */
    @TableField("idempotent_key")
    private String idempotentKey;
    
    /** 优先级 */
    @TableField("priority")
    private Integer priority;
    
    /** 计划执行时间 */
    @TableField("scheduled_at")
    private Instant scheduledAt;
    
    /** 状态编码 */
    @TableField("status_code")
    private String statusCode;
    
    /** 租约拥有者 */
    @TableField("lease_owner")
    private String leaseOwner;
    
    /** 租约到期时间 */
    @TableField("leased_until")
    private Instant leasedUntil;
    
    /** 租约次数 */
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
    
    /** 备注信息（JSON格式） */
    @TableField(value = "record_remarks", typeHandler = JacksonTypeHandler.class)
    private JsonNode recordRemarksJson;
}
