package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.ingest.domain.model.enums.JobStatus;
import com.patra.ingest.domain.model.enums.JobType;
import com.patra.ingest.domain.model.enums.TriggerType;
import com.patra.starter.mybatis.entity.BaseDO.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import java.time.LocalDateTime;

/**
 * 采集任务数据对象
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ing_job")
public class JobDO extends BaseDO {
    
    /**
     * 计划ID（可选）
     */
    @TableField("plan_id")
    private Long planId;
    
    /**
     * 切片ID（可选）
     */
    @TableField("slice_id")
    private Long sliceId;
    
    /**
     * 来源渠道ID
     */
    @TableField("literature_provenance_id")
    private Long literatureProvenanceId;
    
    /**
     * 任务类型
     */
    @TableField("job_type")
    private JobType jobType;
    
    /**
     * 触发类型
     */
    @TableField("trigger_type")
    private TriggerType triggerType;
    
    /**
     * API凭据ID
     */
    @TableField("api_credential_id")
    private Long apiCredentialId;
    
    /**
     * 任务参数JSON
     */
    @TableField("params")
    private String params;
    
    /**
     * 兼容旧的任务键
     */
    @TableField("job_key")
    private String jobKey;
    
    /**
     * 强幂等键
     */
    @TableField("idempotent_key")
    private String idempotentKey;
    
    /**
     * 表达式指纹（冗余）
     */
    @TableField("expr_hash")
    private String exprHash;
    
    /**
     * 调度优先级（1高→9低）
     */
    @TableField("priority")
    private Integer priority;
    
    /**
     * 任务状态
     */
    @TableField("status")
    private JobStatus status;
    
    /**
     * 计划开始时间
     */
    @TableField("scheduled_at")
    private LocalDateTime scheduledAt;
    
    /**
     * 实际开始时间
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
     * 父任务ID
     */
    @TableField("parent_job_id")
    private Long parentJobId;
    
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
    
    /**
     * 失败原因
     */
    @TableField("error")
    private String error;
}
