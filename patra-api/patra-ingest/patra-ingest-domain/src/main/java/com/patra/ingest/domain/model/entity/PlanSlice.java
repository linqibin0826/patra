package com.patra.ingest.domain.model.entity;

import com.patra.ingest.domain.model.enums.SliceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 计划切片 · 实体。
 * <p>
 * 将 Plan 的总窗口按策略切为若干片段；每片附带局部化表达式与规范化切片签名。
 * 并行与幂等的天然边界，通过 sliceSignatureHash 表达。
 * </p>
 *
 * 字段中的 JSON 以字符串形式承载。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanSlice {

    /** 实体标识 */
    private Long id;

    /** 关联 PlanID */
    private Long planId;

    /** 切片序号(0..N) */
    private Integer sliceNo;

    /** 切片签名哈希（规范化边界 JSON 的哈希） */
    private String sliceSignatureHash;

    /** 通用边界说明（时间/ID区间/landmark/预算等，JSON 字符串） */
    private String sliceSpec;

    /** 局部化表达式哈希 */
    private String exprHash;

    /** 局部化表达式 AST 快照（含本 Slice 边界，JSON 字符串） */
    private String exprSnapshot;

    /** 切片状态 */
    private SliceStatus status;
}

