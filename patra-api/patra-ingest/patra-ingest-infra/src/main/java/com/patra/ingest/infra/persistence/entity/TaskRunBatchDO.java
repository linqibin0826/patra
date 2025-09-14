package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.ingest.domain.model.enums.RunBatchStatus;
import com.patra.starter.mybatis.entity.BaseDO.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * 运行批次 · 去前缀实体，对应表：ing_task_run_batch。
 * <p>页码/令牌步进的最小账目；承载断点续跑与去重。</p>
 *
 * 字段要点：
 * - status：{@link RunBatchStatus}
 * - stats：JSON
 * - beforeToken/afterToken、pageNo/pageSize：分页/游标推进信息
 *
 * 继承 BaseDO：id、recordRemarks、created/updatedBy/At、version、ipAddress、deleted。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ing_task_run_batch", autoResultMap = true)
public class TaskRunBatchDO extends BaseDO {

    /** 关联 RunID */
    private Long runId;

    /** 冗余 · TaskID */
    private Long taskId;

    /** 冗余 · SliceID */
    private Long sliceId;

    /** 冗余 · PlanID */
    private Long planId;

    /** 冗余 · 执行表达式哈希 */
    private String exprHash;

    /** 批次序号(1起,连续) */
    private Integer batchNo;

    /** 页码（token 分页为空） */
    private Integer pageNo;

    /** 页大小 */
    private Integer pageSize;

    /** 该批开始令牌/位置（retstart/cursorMark 等） */
    private String beforeToken;

    /** 该批结束令牌/下一位置 */
    private String afterToken;

    /** 幂等键：SHA256(run_id + before_token | page_no) */
    private String idempotentKey;

    /** 本批记录数 */
    private Integer recordCount;

    /** 状态 */
    private RunBatchStatus status;

    /** 提交时间(UTC) */
    private LocalDateTime committedAt;

    /** 失败原因 */
    private String error;

    /** 统计（JSON） */
    private JsonNode stats;
}

