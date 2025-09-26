package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO.BaseDO;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ing_cursor")
/**
 * 采集游标表 (ing_cursor)
 * 针对来源+操作+命名空间的一类增量位置持久化（时间/数值/字符串混合）。
 * normalized_* 字段用于排序/范围比较的统一化。
 */
public class CursorDO extends BaseDO {
    /**
     * 来源代码
     */
    @TableField("provenance_code")
    private String provenanceCode;
    /**
     * 操作代码
     */
    @TableField("operation_code")
    private String operationCode;
    /**
     * 游标逻辑键（如 UPDATED_AT / ID / PAGE_TOKEN）
     */
    @TableField("cursor_key")
    private String cursorKey;
    /**
     * 命名空间作用域（GLOBAL/PROVENANCE/ENDPOINT 等）
     */
    @TableField("namespace_scope_code")
    private String namespaceScopeCode;
    /**
     * 命名空间键（具体某 endpoint / 组合键）
     */
    @TableField("namespace_key")
    private String namespaceKey;
    /**
     * 游标类型（TIME/NUMERIC/STRING）
     */
    @TableField("cursor_type_code")
    private String cursorTypeCode;
    /**
     * 原始游标值（JSON 或原始字符串表示）
     */
    @TableField("cursor_value")
    private String cursorValue;
    /**
     * 观测到的最大值（用于推进策略判断）
     */
    @TableField("observed_max_value")
    private String observedMaxValue;
    /**
     * 标准化时间值（若类型=TIME）
     */
    @TableField("normalized_instant")
    private java.time.Instant normalizedInstant;
    /**
     * 标准化数值（若类型=NUMERIC）
     */
    @TableField("normalized_numeric")
    private java.math.BigDecimal normalizedNumeric;
    /**
     * 最近关联的调度实例 ID（追踪更新来源）
     */
    @TableField("schedule_instance_id")
    private Long scheduleInstanceId;
    /**
     * 最近关联计划 ID
     */
    @TableField("plan_id")
    private Long planId;
    /**
     * 最近关联切片 ID
     */
    @TableField("slice_id")
    private Long sliceId;
    /**
     * 最近关联任务 ID
     */
    @TableField("task_id")
    private Long taskId;
    /**
     * 最近运行 ID
     */
    @TableField("last_run_id")
    private Long lastRunId;
    /**
     * 最近批次 ID
     */
    @TableField("last_batch_id")
    private Long lastBatchId;
    /**
     * 表达式 hash（最近推进时的执行上下文）
     */
    @TableField("expr_hash")
    private String exprHash;
}
