package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO.BaseDO;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = true)
@TableName("ing_plan_slice")
/**
 * 计划切片表 (ing_plan_slice)
 * 将 Plan 拆分成多个可并行/序列执行的窗口或区段（时间/ID/Token），支持局部失败重试。
 */
public class PlanSliceDO extends BaseDO {
    /** 所属计划 ID */
    @TableField("plan_id") private Long planId;
    /** 来源代码（冗余） */
    @TableField("provenance_code") private String provenanceCode;
    /** 切片序号（稳定顺序） */
    @TableField("slice_no") private Integer sliceNo;
    /** 切片签名哈希（规范化 spec + expr_hash 计算） */
    @TableField("slice_signature_hash") private String sliceSignatureHash;
    /** 切片规格 JSON（时间/ID/Token 界限等） */
    @TableField("slice_spec") private String sliceSpec;
    /** 表达式哈希（切片后具体执行表达式版本） */
    @TableField("expr_hash") private String exprHash;
    /** 表达式快照（局部化后的AST） */
    @TableField("expr_snapshot") private String exprSnapshot;
    /** 状态代码（PENDING/DISPATCHED/SUCCEEDED/FAILED） */
    @TableField("status_code") private String statusCode;
}
