package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

/**
 * <p><b>任务运行批次 DO</b> —— 映射表：<code>ing_task_run_batch</code></p>
 * <p>语义：Task Run 在执行过程中的分页/令牌步进账目，是断点续跑与去重的最小颗粒。</p>
 * <p>要点：
 * <ul>
 *   <li><code>idempotent_key</code> 唯一，保证重复回写不会产生重复批次。</li>
 *   <li><code>before_token</code>/<code>after_token</code> 记录分页游标；配合唯一索引回溯。</li>
 *   <li><code>stats</code> 用 JSON 存储批次级指标（fetched/upserted 等）。</li>
 * </ul>
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ing_task_run_batch", autoResultMap = true)
public class TaskRunBatchDO extends BaseDO {

    /** 关联运行 ID */
    @TableField("run_id")
    private Long runId;

    /** 关联任务 ID（冗余） */
    @TableField("task_id")
    private Long taskId;

    /** 关联切片 ID（冗余） */
    @TableField("slice_id")
    private Long sliceId;

    /** 关联计划 ID（冗余） */
    @TableField("plan_id")
    private Long planId;

    /** 执行表达式哈希（冗余） */
    @TableField("expr_hash")
    private String exprHash;

    /** 来源代码冗余 */
    @TableField("provenance_code")
    private String provenanceCode;

    /** 操作类型冗余 */
    @TableField("operation_code")
    private String operationCode;

    /** 批次序号（1 起，连续） */
    @TableField("batch_no")
    private Integer batchNo;

    /** 页码（token 分页时为空） */
    @TableField("page_no")
    private Integer pageNo;

    /** 页大小 */
    @TableField("page_size")
    private Integer pageSize;

    /** 批次开始前的位置 token */
    @TableField("before_token")
    private String beforeToken;

    /** 批次结束后的位置 token */
    @TableField("after_token")
    private String afterToken;

    /** 批次幂等键（UK：uk_batch_idem） */
    @TableField("idempotent_key")
    private String idempotentKey;

    /** 记录数 */
    @TableField("record_count")
    private Integer recordCount;

    /** 批次状态（DICT：ing_batch_status） */
    @TableField("status_code")
    private String statusCode;

    /** 批次提交时间 */
    @TableField("committed_at")
    private Instant committedAt;

    /** 失败原因（TEXT） */
    @TableField("error")
    private String error;

    /** 统计指标（JSON） */
    @TableField(value = "stats", typeHandler = JacksonTypeHandler.class)
    private JsonNode stats;
}
