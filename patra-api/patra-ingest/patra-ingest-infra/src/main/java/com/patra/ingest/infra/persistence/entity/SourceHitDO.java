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
 * 采集命中记录数据对象
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ing_source_hit")
public class SourceHitDO extends BaseDO {
    
    /**
     * 命中归并后对应的作品ID
     */
    @TableField("work_id")
    private Long workId;
    
    /**
     * 来源渠道ID
     */
    @TableField("literature_provenance_id")
    private Long literatureProvenanceId;
    
    /**
     * 源端唯一ID
     */
    @TableField("source_specific_id")
    private String sourceSpecificId;
    
    /**
     * 命中/拉取时间
     */
    @TableField("retrieved_at")
    private LocalDateTime retrievedAt;
    
    /**
     * 源端声称的更新时间
     */
    @TableField("source_updated_at")
    private LocalDateTime sourceUpdatedAt;
    
    /**
     * 原始结构/解析版本
     */
    @TableField("raw_schema_version")
    private String rawSchemaVersion;
    
    /**
     * 原始返回或解析后JSON
     */
    @TableField("raw_data_json")
    private String rawDataJson;
    
    /**
     * 原始JSON的SHA-256哈希
     */
    @TableField("raw_data_hash")
    private String rawDataHash;
    
    /**
     * 产生本命中的作业ID（冗余）
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
     * 表达式指纹（冗余）
     */
    @TableField("expr_hash")
    private String exprHash;
}
