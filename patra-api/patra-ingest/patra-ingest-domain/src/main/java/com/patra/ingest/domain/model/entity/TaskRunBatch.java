package com.patra.ingest.domain.model.entity;

import com.patra.ingest.domain.model.enums.RunBatchStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 运行批次 · 实体。
 * <p>
 * 将一次运行 attempt 的数据拉取与落库过程细分为可提交的最小账目单位（页码或令牌步进的批次），
 * 承载断点续跑与去重。三重幂等：(runId,batchNo)、(runId,beforeToken)、idempotentKey。
 * </p>
 * <p>
 * JSON 字段以字符串承载。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskRunBatch {

    /**
     * 实体标识
     */
    private Long id;

    /**
     * 关联 RunID
     */
    private Long runId;

    /**
     * 冗余：TaskID
     */
    private Long taskId;

    /**
     * 冗余：SliceID
     */
    private Long sliceId;

    /**
     * 冗余：PlanID
     */
    private Long planId;

    /**
     * 冗余：执行表达式哈希
     */
    private String exprHash;

    /**
     * 批次序号(1起,连续)
     */
    private Integer batchNo;

    /**
     * 页码（token 分页为空）
     */
    private Integer pageNo;

    /**
     * 页大小
     */
    private Integer pageSize;

    /**
     * 该批开始令牌/位置（retstart/cursorMark 等）
     */
    private String beforeToken;

    /**
     * 该批结束令牌/下一位置
     */
    private String afterToken;

    /**
     * 幂等键：SHA256(run_id + before_token | page_no)
     */
    private String idempotentKey;

    /**
     * 本批记录数
     */
    private Integer recordCount;

    /**
     * 状态
     */
    private RunBatchStatus status;

    /**
     * 提交时间(UTC)
     */
    private LocalDateTime committedAt;

    /**
     * 失败原因
     */
    private String error;

    /**
     * 统计（JSON 字符串）
     */
    private String stats;
}

