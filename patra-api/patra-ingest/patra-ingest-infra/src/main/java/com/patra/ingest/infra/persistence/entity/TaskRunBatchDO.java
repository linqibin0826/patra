package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO.BaseDO;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = true)
@TableName("ing_task_run_batch")
/**
 * 任务运行批次表 (ing_task_run_batch)
 * 一个 TaskRun 可能拆分为多个批次（分页/分块），便于增量提交与重试。
 * 批次级别追踪分页 token / record_count / 提交时间。
 */
public class TaskRunBatchDO extends BaseDO {
    /** 关联运行 ID */
    @TableField("run_id") private Long runId;
    /** 关联任务 ID（冗余方便批次直接回溯任务） */
    @TableField("task_id") private Long taskId;
    /** 关联切片 ID（冗余） */
    @TableField("slice_id") private Long sliceId;
    /** 关联计划 ID（冗余） */
    @TableField("plan_id") private Long planId;
    /** 表达式哈希（冗余，一致性校验） */
    @TableField("expr_hash") private String exprHash;
    /** 来源代码 */
    @TableField("provenance_code") private String provenanceCode;
    /** 操作代码 */
    @TableField("operation_code") private String operationCode;
    /** 批次序号（从1或0递增） */
    @TableField("batch_no") private Integer batchNo;
    /** 页号（如果分页） */
    @TableField("page_no") private Integer pageNo;
    /** 页大小 */
    @TableField("page_size") private Integer pageSize;
    /** 批次开始前 token（游标） */
    @TableField("before_token") private String beforeToken;
    /** 批次结束后 token（游标） */
    @TableField("after_token") private String afterToken;
    /** 幂等键（防重复提交同一批次） */
    @TableField("idempotent_key") private String idempotentKey;
    /** 本批次记录数 */
    @TableField("record_count") private Integer recordCount;
    /** 状态代码（PENDING/COMMITTED/FAILED 等） */
    @TableField("status_code") private String statusCode;
    /** 提交时间（成功入库或确认完成） */
    @TableField("committed_at") private java.time.Instant committedAt;
    /** 错误 JSON */
    @TableField("error") private String error;
    /** 统计 JSON */
    @TableField("stats") private String stats;
}
