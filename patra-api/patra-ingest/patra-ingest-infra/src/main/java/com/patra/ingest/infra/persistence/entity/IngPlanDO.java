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
 * 计划 DO - 对应 ing_plan 表。
 * 
 * <p>存储计划蓝图信息，包含配置快照和窗口信息。
 * 
 * @author linqibin
 * @since 0.1.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ing_plan", autoResultMap = true)
public class IngPlanDO extends BaseDO {
    
    /** 调度实例ID */
    @TableField("schedule_instance_id")
    private Long scheduleInstanceId;
    
    /** 计划键（业务可读标识） */
    @TableField("plan_key")
    private String planKey;
    
    /** 数据源编码（冗余字段） */
    @TableField("provenance_code")
    private String provenanceCode;
    
    /** 端点名称 */
    @TableField("endpoint_name")
    private String endpointName;
    
    /** 操作类型编码 */
    @TableField("operation_code")
    private String operationCode;
    
    /** 表达式原型哈希 */
    @TableField("expr_proto_hash")
    private String exprProtoHash;
    
    /** 表达式原型快照（JSON格式） */
    @TableField(value = "expr_proto_snapshot", typeHandler = JacksonTypeHandler.class)
    private JsonNode exprProtoSnapshot;
    
    /** 来源配置快照（JSON格式） */
    @TableField(value = "provenance_config_snapshot", typeHandler = JacksonTypeHandler.class)
    private JsonNode provenanceConfigSnapshot;
    
    /** 来源配置哈希 */
    @TableField("provenance_config_hash")
    private String provenanceConfigHash;
    
    /** 窗口开始时间 */
    @TableField("window_from")
    private Instant windowFrom;
    
    /** 窗口结束时间 */
    @TableField("window_to")
    private Instant windowTo;
    
    /** 切片策略编码 */
    @TableField("slice_strategy_code")
    private String sliceStrategyCode;
    
    /** 切片参数（JSON格式） */
    @TableField(value = "slice_params", typeHandler = JacksonTypeHandler.class)
    private JsonNode sliceParams;
    
    /** 状态编码 */
    @TableField("status_code")
    private String statusCode;
    
    /** 备注信息（JSON格式） */
    @TableField(value = "record_remarks", typeHandler = JacksonTypeHandler.class)
    private JsonNode recordRemarks;
}
