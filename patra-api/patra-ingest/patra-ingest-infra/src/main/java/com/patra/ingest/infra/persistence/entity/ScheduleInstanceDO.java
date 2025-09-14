package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.enums.SchedulerSource;
import com.patra.ingest.domain.model.enums.TriggerType;
import com.patra.starter.mybatis.entity.BaseDO.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * 调度实例 · 去前缀实体，对应表：ing_schedule_instance。
 * <p>一次外部触发与其发生当时的配置/表达式原型快照，作为本次编排的“根”。</p>
 * <p>
 * 字段要点：
 * - scheduler：{@link SchedulerSource}（xxl/manual/other）。
 * - triggerType：{@link TriggerType}（manual/schedule/replay）。
 * - triggeredAt：UTC 时间。
 * - triggerParams/provenanceConfigSnapshot/exprProtoSnapshot：规范化 JSON。
 * <p>
 * 继承 BaseDO：id、recordRemarks、created/updatedBy/At、version、ipAddress、deleted。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ing_schedule_instance", autoResultMap = true)
public class ScheduleInstanceDO extends BaseDO {

    /**
     * 调度器来源
     */
    private SchedulerSource scheduler;


    /**
     * 外部 JobID（如 XXL 的 jobId）
     */
    private String schedulerJobId;

    /**
     * 触发类型
     */
    private TriggerType triggerType;

    /**
     * 触发时间(UTC)
     */
    private LocalDateTime triggeredAt;

    /**
     * 调度入参(规范化 JSON)
     */
    private JsonNode triggerParams;

    /**
     * 来源代码：pubmed/epmc/crossref 等
     */
    private ProvenanceCode literatureProvenanceCode;

    /**
     * 来源配置/窗口/限流/重试等快照（中立模型 JSON）
     */
    private JsonNode provenanceConfigSnapshot;

    /**
     * 表达式原型哈希（规范化 AST 的 SHA-256 十六进制）
     */
    private String exprProtoHash;

    /**
     * 表达式原型 AST 快照（不含切片条件）
     */
    private JsonNode exprProtoSnapshot;
}

