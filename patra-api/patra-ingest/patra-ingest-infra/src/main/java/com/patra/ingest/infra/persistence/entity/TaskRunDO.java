package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO.BaseDO;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = true)
@TableName("ing_task_run")
/**
 * 任务运行实例表 (ing_task_run)
 * 描述一次 Task 的具体执行尝试（包含重试 attempt）。
 * 冗余 provenance_code/operation_code 便于直接按来源/操作统计最近运行情况。
 * 心跳(last_heartbeat) 支持运行中健康监测 / 超时判定。
 */
public class TaskRunDO extends BaseDO {
    /** 所属任务 ID */
    @TableField("task_id") private Long taskId;
    /** 第几次尝试（从1开始，或0——视上层策略） */
    @TableField("attempt_no") private Integer attemptNo;
    /** 来源代码（冗余） */
    @TableField("provenance_code") private String provenanceCode;
    /** 操作代码（冗余） */
    @TableField("operation_code") private String operationCode;
    /** 运行状态代码（RUNNING/SUCCEEDED/FAILED 等） */
    @TableField("status_code") private String statusCode;
    /** 运行检查点位置（例如分页 token / 游标） */
    @TableField("checkpoint") private String checkpoint;
    /** 统计 JSON（耗时/计数等聚合指标） */
    @TableField("stats") private String stats;
    /** 错误 JSON（标准化异常/错误码等） */
    @TableField("error") private String error;
    /** 窗口起（逻辑数据时间下界） */
    @TableField("window_from") private java.time.Instant windowFrom;
    /** 窗口止（逻辑数据时间上界） */
    @TableField("window_to") private java.time.Instant windowTo;
    /** 开始时间 */
    @TableField("started_at") private java.time.Instant startedAt;
    /** 完成时间 */
    @TableField("finished_at") private java.time.Instant finishedAt;
    /** 最近心跳时间（空表示已结束或未启动） */
    @TableField("last_heartbeat") private java.time.Instant lastHeartbeat;
}
