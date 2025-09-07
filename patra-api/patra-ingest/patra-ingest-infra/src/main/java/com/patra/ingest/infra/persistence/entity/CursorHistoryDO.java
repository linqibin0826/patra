package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.ingest.domain.model.enums.Direction;
import com.patra.starter.mybatis.entity.BaseDO.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * 游标推进历史数据对象
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ing_cursor_history")
public class CursorHistoryDO extends BaseDO {
    
    /**
     * 来源渠道ID
     */
    @TableField("literature_provenance_id")
    private Long literatureProvenanceId;
    
    /**
     * 游标键
     */
    @TableField("cursor_key")
    private String cursorKey;
    
    /**
     * 推进前的值
     */
    @TableField("prev_value")
    private String prevValue;
    
    /**
     * 推进后的值
     */
    @TableField("new_value")
    private String newValue;
    
    /**
     * 本次观测到的最大边界
     */
    @TableField("observed_max")
    private String observedMax;
    
    /**
     * 由哪个作业推进
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
    
    /**
     * 推进方向
     */
    @TableField("direction")
    private Direction direction;
    
    /**
     * 游标类型
     */
    @TableField("cursor_type")
    private String cursorType;
    
    /**
     * 本次推进覆盖的窗口起
     */
    @TableField("window_from")
    private LocalDateTime windowFrom;
    
    /**
     * 本次推进覆盖的窗口止
     */
    @TableField("window_to")
    private LocalDateTime windowTo;
}
