package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO.BaseDO;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ing_schedule_instance")
/**
 * 调度实例表 (ing_schedule_instance)
 * 记录外部/内部调度触发一次“计划生成”流程的上下文快照。
 */
public class ScheduleInstanceDO extends BaseDO {
    /** 调度器标识（例如 XXL-JOB / CRON / MANUAL） */
    @TableField("scheduler_code") private String schedulerCode;
    /** 调度器内部任务 ID（如 xxl-job 的 jobId） */
    @TableField("scheduler_job_id") private String schedulerJobId;
    /** 调度器运行日志 ID（用于回溯日志） */
    @TableField("scheduler_log_id") private String schedulerLogId;
    /** 触发类型代码（CRON/MANUAL/RETRY 等） */
    @TableField("trigger_type_code") private String triggerTypeCode;
    /** 触发时间 */
    @TableField("triggered_at") private java.time.Instant triggeredAt;
    /** 来源代码（本次调度针对的文献源） */
    @TableField("provenance_code") private String provenanceCode;
}
