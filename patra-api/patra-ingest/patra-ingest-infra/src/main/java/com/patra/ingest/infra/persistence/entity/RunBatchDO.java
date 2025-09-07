package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.ingest.domain.model.enums.BatchStatus;
import com.patra.starter.mybatis.entity.BaseDO.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import java.time.LocalDateTime;

/**
 * 运行批次数据对象
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ing_run_batch")
public class RunBatchDO extends BaseDO {
    
    /**
     * 所属运行ID
     */
    @TableField("run_id")
    private Long runId;
    
    /**
     * 关联作业ID（冗余）
     */
    @TableField("job_id")
    private Long jobId;
    
    /**
     * 切片ID（冗余）
     */
    @TableField("slice_id")
    private Long sliceId;
    
    /**
     * 计划ID（冗余）
     */
    @TableField("plan_id")
    private Long planId;
    
    /**
     * 表达式哈希（冗余）
     */
    @TableField("expr_hash")
    private String exprHash;
    
    /**
     * 批次序号（1起，连续）
     */
    @TableField("batch_no")
    private Integer batchNo;
    
    /**
     * 页码（offset/limit分页，从1开始；token分页为空）
     */
    @TableField("page_no")
    private Integer pageNo;
    
    /**
     * 页大小
     */
    @TableField("page_size")
    private Integer pageSize;
    
    /**
     * 本批开始令牌/位置
     */
    @TableField("before_token")
    private String beforeToken;
    
    /**
     * 本批结束令牌/下一位置
     */
    @TableField("after_token")
    private String afterToken;
    
    /**
     * 批次幂等键
     */
    @TableField("idempotent_key")
    private String idempotentKey;
    
    /**
     * 本批处理记录数
     */
    @TableField("record_count")
    private Integer recordCount;
    
    /**
     * 批次状态
     */
    @TableField("status")
    private BatchStatus status;
    
    /**
     * 批次提交时间
     */
    @TableField("committed_at")
    private LocalDateTime committedAt;
    
    /**
     * 失败原因
     */
    @TableField("error")
    private String error;
    
    /**
     * 扩展统计（JSON）
     */
    @TableField("stats")
    private String stats;
}
