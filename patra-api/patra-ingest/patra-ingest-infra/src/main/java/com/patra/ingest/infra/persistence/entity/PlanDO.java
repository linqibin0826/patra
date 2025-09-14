package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.ingest.domain.model.enums.PlanStatus;
import com.patra.ingest.domain.model.enums.SliceStrategy;
import com.patra.starter.mybatis.entity.BaseDO.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * 计划蓝图 · 去前缀实体，对应表：ing_plan。
 * <p>定义总时间窗口与切片策略，以及表达式原型快照。</p>
 * <p>
 * 字段要点：
 * - sliceStrategy：{@link SliceStrategy}
 * - status：{@link PlanStatus}
 * - windowFrom/windowTo：UTC
 * - sliceParams/exprProtoSnapshot：JSON
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
@TableName(value = "ing_plan", autoResultMap = true)
public class PlanDO extends BaseDO {

    /**
     * 关联调度实例ID
     */
    private Long scheduleInstanceId;

    /**
     * 人类可读/外部幂等键（唯一）
     */
    private String planKey;

    /**
     * 表达式原型哈希
     */
    private String exprProtoHash;

    /**
     * 表达式原型 AST 快照
     */
    private JsonNode exprProtoSnapshot;

    /**
     * 总窗起(UTC, 含)
     */
    private LocalDateTime windowFrom;

    /**
     * 总窗止(UTC, 不含)
     */
    private LocalDateTime windowTo;

    /**
     * 切片策略
     */
    private SliceStrategy sliceStrategy;

    /**
     * 切片参数（JSON）
     */
    private JsonNode sliceParams;

    /**
     * 计划状态
     */
    private PlanStatus status;
}

