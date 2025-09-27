package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p><b>计划切片 DO</b> —— 映射表：<code>ing_plan_slice</code></p>
 * <p>语义：将计划蓝图按策略切分出的最小幂等执行单元；每个切片派生一个任务。</p>
 * <p>要点：
 * <ul>
 *   <li><code>slice_signature_hash</code> 对 <code>slice_spec</code> 做规范化哈希，配合唯一索引防止重复生成。</li>
 *   <li><code>slice_spec</code>、<code>expr_snapshot</code> 均为 JSON AST，使用 {@link JacksonTypeHandler} 保持结构化。</li>
 * </ul>
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ing_plan_slice", autoResultMap = true)
public class PlanSliceDO extends BaseDO {

    /** 关联计划 ID */
    @TableField("plan_id")
    private Long planId;

    /** 冗余的来源代码 */
    @TableField("provenance_code")
    private String provenanceCode;

    /** 切片序号（0..N） */
    @TableField("slice_no")
    private Integer sliceNo;

    /** 切片签名哈希（基于规范化 slice_spec） */
    @TableField("slice_signature_hash")
    private String sliceSignatureHash;

    /** 切片边界描述（JSON） */
    @TableField(value = "slice_spec", typeHandler = JacksonTypeHandler.class)
    private JsonNode sliceSpec;

    /** 局部化表达式哈希 */
    @TableField("expr_hash")
    private String exprHash;

    /** 局部化表达式快照（JSON AST，可重放） */
    @TableField(value = "expr_snapshot", typeHandler = JacksonTypeHandler.class)
    private JsonNode exprSnapshot;

    /** 切片状态（DICT：ing_slice_status） */
    @TableField("status_code")
    private String statusCode;
}
