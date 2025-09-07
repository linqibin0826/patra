package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * 字段级溯源数据对象
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ing_field_provenance")
public class FieldProvenanceDO extends BaseDO {
    
    /**
     * 被赋值的作品/记录ID
     */
    @TableField("work_id")
    private Long workId;
    
    /**
     * 字段名
     */
    @TableField("field_name")
    private String fieldName;
    
    /**
     * 该字段值来自哪次命中
     */
    @TableField("source_hit_id")
    private Long sourceHitId;
    
    /**
     * 字段值哈希
     */
    @TableField("value_hash")
    private String valueHash;
    
    /**
     * 规范化/取值规则版本
     */
    @TableField("normalize_schema")
    private String normalizeSchema;
    
    /**
     * 字段值快照（JSON）
     */
    @TableField("value_json")
    private String valueJson;
    
    /**
     * 预览/摘要
     */
    @TableField("value_preview")
    private String valuePreview;
    
    /**
     * 字段取值时间
     */
    @TableField("collected_at")
    private LocalDateTime collectedAt;
    
    /**
     * 执行该赋值的作业ID（冗余）
     */
    @TableField("job_id")
    private Long jobId;
    
    /**
     * 计划ID（冗余）
     */
    @TableField("plan_id")
    private Long planId;
    
    /**
     * 切片ID（冗余）
     */
    @TableField("slice_id")
    private Long sliceId;
    
    /**
     * 运行ID（冗余）
     */
    @TableField("run_id")
    private Long runId;
    
    /**
     * 批次ID（冗余）
     */
    @TableField("batch_id")
    private Long batchId;
    
    /**
     * 表达式哈希（冗余）
     */
    @TableField("expr_hash")
    private String exprHash;
}
