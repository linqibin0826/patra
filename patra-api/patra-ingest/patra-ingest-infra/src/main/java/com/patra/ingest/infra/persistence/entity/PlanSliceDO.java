package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.ingest.domain.model.enums.SliceStatus;
import com.patra.starter.mybatis.entity.BaseDO.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import java.time.LocalDateTime;

/**
 * 计划切片数据对象
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ing_plan_slice")
public class PlanSliceDO extends BaseDO {
    
    /**
     * 计划ID
     */
    @TableField("plan_id")
    private Long planId;
    
    /**
     * 切片序号（0..N）
     */
    @TableField("slice_no")
    private Integer sliceNo;
    
    /**
     * 切片起始（含）
     */
    @TableField("slice_from")
    private LocalDateTime sliceFrom;
    
    /**
     * 切片结束（含）
     */
    @TableField("slice_to")
    private LocalDateTime sliceTo;
    
    /**
     * 切片表达式哈希（派生+局部化）
     */
    @TableField("expr_hash")
    private String exprHash;
    
    /**
     * 切片专属表达式快照（含局部化的时间条件）
     */
    @TableField("expr_snapshot_json")
    private String exprSnapshotJson;
    
    /**
     * 切片状态
     */
    @TableField("status")
    private SliceStatus status;
    
    /**
     * 最近一次Job ID（冗余）
     */
    @TableField("last_job_id")
    private Long lastJobId;
    
    /**
     * 该切片产生的分页批次数（回填）
     */
    @TableField("total_batches")
    private Integer totalBatches;
    
    /**
     * 该切片累计命中/写入条数（回填）
     */
    @TableField("total_hits")
    private Long totalHits;
    
    /**
     * 失败批次数（回填）
     */
    @TableField("error_count")
    private Integer errorCount;
}
