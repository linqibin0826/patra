package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 计划切片 DO - 对应 ing_plan_slice 表。
 * 
 * <p>存储切片规格和本地化表达式信息。
 * 
 * @author linqibin
 * @since 0.1.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ing_plan_slice", autoResultMap = true)
public class IngPlanSliceDO extends BaseDO {
    
    /** 计划ID */
    @TableField("plan_id")
    private Long planId;
    
    /** 数据源编码（冗余字段） */
    @TableField("provenance_code")
    private String provenanceCode;
    
    /** 切片序号 */
    @TableField("slice_no")
    private Integer sliceNo;
    
    /** 切片签名哈希（用于幂等） */
    @TableField("slice_signature_hash")
    private String sliceSignatureHash;
    
    /** 切片规格（JSON格式） */
    @TableField(value = "slice_spec", typeHandler = JacksonTypeHandler.class)
    private JsonNode sliceSpec;
    
    /** 本地化表达式快照（JSON格式） */
    @TableField(value = "expr_snapshot", typeHandler = JacksonTypeHandler.class)
    private JsonNode exprSnapshot;
    
    /** 表达式哈希 */
    @TableField("expr_hash")
    private String exprHash;
    
    /** 状态编码 */
    @TableField("status_code")
    private String statusCode;
    
    /** 失败原因 */
    @TableField("failure_reason")
    private String failureReason;
    
    /** 备注信息（JSON格式） */
    @TableField(value = "record_remarks", typeHandler = JacksonTypeHandler.class)
    private JsonNode recordRemarksJson;
}
