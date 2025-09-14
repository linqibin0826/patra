package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.ingest.domain.model.enums.SliceStatus;
import com.patra.starter.mybatis.entity.BaseDO.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 计划切片 · 去前缀实体，对应表：ing_plan_slice。
 * <p>通用分片（时间/ID/token/预算），是并行与幂等的边界。</p>
 *
 * 字段要点：
 * - status：{@link SliceStatus}
 * - sliceSpec/exprSnapshot：JSON
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
@TableName(value = "ing_plan_slice", autoResultMap = true)
public class PlanSliceDO extends BaseDO {

    /** 关联 PlanID */
    private Long planId;

    /** 切片序号(0..N) */
    private Integer sliceNo;

    /** 切片签名哈希(规范化的通用边界JSON) */
    private String sliceSignatureHash;

    /** 通用边界说明：时间/ID区间/landmark/预算等（JSON） */
    private JsonNode sliceSpec;

    /** 局部化表达式哈希 */
    private String exprHash;

    /** 局部化表达式 AST 快照（含本Slice边界） */
    private JsonNode exprSnapshot;

    /** 切片状态 */
    private SliceStatus status;
}

