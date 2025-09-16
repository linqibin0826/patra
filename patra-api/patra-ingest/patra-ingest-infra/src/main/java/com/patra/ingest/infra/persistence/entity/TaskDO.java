package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.enums.IngestOperationType;
import com.patra.ingest.domain.model.enums.TaskStatus;
import com.patra.starter.mybatis.entity.BaseDO.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * 任务 · 去前缀实体，对应表：ing_task。
 * <p>每个计划切片生成一个任务；支持强幂等与调度/执行状态。</p>
 *
 * 字段要点：
 * - operation：{@link IngestOperationType}
 * - status：{@link TaskStatus}
 * - params：JSON
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
@TableName(value = "ing_task", autoResultMap = true)
public class TaskDO extends BaseDO {

    /** 冗余 · 调度实例ID */
    private Long scheduleInstanceId;

    /** 冗余 · PlanID */
    private Long planId;

    /** 冗余 · SliceID */
    private Long sliceId;

    /** 来源代码：pubmed/epmc/crossref 等 */
    private ProvenanceCode literatureProvenanceCode;

    /** 操作类型 */
    private IngestOperationType operation;

    /** 所用凭据ID（可空=匿名/公共） */
    private Long apiCredentialId;

    /** 任务参数(规范化 JSON) */
    private JsonNode params;

    /** 幂等键：SHA256(slice_signature + expr_hash + operation + trigger + normalized(params)) */
    private String idempotentKey;

    /** 冗余：执行表达式哈希 */
    private String exprHash;

    /** 1高→9低 */
    private Integer priority;

    /** 任务状态 */
    private TaskStatus status;

    /** 计划开始(UTC) */
    private LocalDateTime scheduledAt;

    /** 实际开始(UTC) */
    private LocalDateTime startedAt;

    /** 结束(UTC) */
    private LocalDateTime finishedAt;

    /** 外部调度运行ID（若逐片触发才用） */
    private String schedulerRunId;

    /** Trace/CID */
    private String correlationId;
}

