package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.ingest.domain.model.enums.RunStatus;
import com.patra.starter.mybatis.entity.BaseDO.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import java.time.LocalDateTime;

/**
 * 运行台账数据对象
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ing_run")
public class RunDO extends BaseDO {
    
    /**
     * 关联任务ID
     */
    @TableField("job_id")
    private Long jobId;
    
    /**
     * 游标键
     */
    @TableField("cursor_key")
    private String cursorKey;
    
    /**
     * 窗口起（含）
     */
    @TableField("window_from")
    private LocalDateTime windowFrom;
    
    /**
     * 窗口止（不含）
     */
    @TableField("window_to")
    private LocalDateTime windowTo;
    
    /**
     * 同一窗口第几次尝试（1起）
     */
    @TableField("attempt_no")
    private Integer attemptNo;
    
    /**
     * 运行状态
     */
    @TableField("status")
    private RunStatus status;
    
    /**
     * 分页/令牌检查点（JSON）
     */
    @TableField("checkpoint")
    private String checkpoint;
    
    /**
     * 统计信息（JSON）
     */
    @TableField("stats")
    private String stats;
    
    /**
     * 失败原因
     */
    @TableField("error")
    private String error;
    
    /**
     * 开始时间
     */
    @TableField("started_at")
    private LocalDateTime startedAt;
    
    /**
     * 结束时间
     */
    @TableField("finished_at")
    private LocalDateTime finishedAt;
    
    /**
     * 最近心跳时间
     */
    @TableField("last_heartbeat")
    private LocalDateTime lastHeartbeat;
    
    /**
     * 调度运行ID
     */
    @TableField("scheduler_run_id")
    private String schedulerRunId;
    
    /**
     * 关联ID
     */
    @TableField("correlation_id")
    private String correlationId;
}
