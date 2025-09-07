package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.ingest.domain.model.enums.MetricType;
import com.patra.starter.mybatis.entity.BaseDO.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * 外部计量指标快照数据对象
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ing_metric_snapshot")
public class MetricSnapshotDO extends BaseDO {
    
    /**
     * 作品ID
     */
    @TableField("work_id")
    private Long workId;
    
    /**
     * 指标类型
     */
    @TableField("metric_type")
    private MetricType metricType;
    
    /**
     * 指标来源
     */
    @TableField("source")
    private String source;
    
    /**
     * 指标值
     */
    @TableField("value")
    private Long value;
    
    /**
     * 相对上次快照的变化量
     */
    @TableField("value_delta")
    private Long valueDelta;
    
    /**
     * 单位
     */
    @TableField("unit")
    private String unit;
    
    /**
     * 采集时间
     */
    @TableField("collected_at")
    private LocalDateTime collectedAt;
    
    /**
     * 产生本快照的作业ID（冗余）
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
