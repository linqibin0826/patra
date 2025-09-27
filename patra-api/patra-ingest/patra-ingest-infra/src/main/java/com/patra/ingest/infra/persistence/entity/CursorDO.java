package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * <p><b>通用游标 DO</b> —— 映射表：<code>ing_cursor</code></p>
 * <p>语义：维护来源 + 操作 + 命名空间的当前水位，兼容时间 / 数值 / Token 三类游标。</p>
 * <p>要点：
 * <ul>
 *   <li><code>namespace_scope_code</code> + <code>namespace_key</code> 区分不同命名空间（全局 / 表达式 / 自定义）。</li>
 *   <li><code>normalized_instant</code>/<code>normalized_numeric</code> 将不同游标类型归一化以便排序和范围查询。</li>
 *   <li>链路冗余字段（schedule/plan/slice/task/run/batch）支持快速回溯来源。</li>
 * </ul>
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ing_cursor")
public class CursorDO extends BaseDO {

    /** 来源代码（与注册中心一致） */
    @TableField("provenance_code")
    private String provenanceCode;

    /** 操作类型编码 */
    @TableField("operation_code")
    private String operationCode;

    /** 游标键（如 updated_at / seq_id / cursor_token） */
    @TableField("cursor_key")
    private String cursorKey;

    /** 命名空间作用域（GLOBAL/EXPR/CUSTOM） */
    @TableField("namespace_scope_code")
    private String namespaceScopeCode;

    /** 命名空间键（expr_hash 或自定义哈希） */
    @TableField("namespace_key")
    private String namespaceKey;

    /** 游标类型（DICT：ing_cursor_type） */
    @TableField("cursor_type_code")
    private String cursorTypeCode;

    /** 当前游标原始值 */
    @TableField("cursor_value")
    private String cursorValue;

    /** 观测到的最大值 */
    @TableField("observed_max_value")
    private String observedMaxValue;

    /** 归一化时间值（类型=TIME 时填充） */
    @TableField("normalized_instant")
    private Instant normalizedInstant;

    /** 归一化数值（类型=ID 时填充） */
    @TableField("normalized_numeric")
    private BigDecimal normalizedNumeric;

    /** 最近推进的调度实例 */
    @TableField("schedule_instance_id")
    private Long scheduleInstanceId;

    /** 最近推进关联的计划 */
    @TableField("plan_id")
    private Long planId;

    /** 最近推进关联的切片 */
    @TableField("slice_id")
    private Long sliceId;

    /** 最近推进关联的任务 */
    @TableField("task_id")
    private Long taskId;

    /** 最近推进关联的运行 */
    @TableField("last_run_id")
    private Long lastRunId;

    /** 最近推进关联的批次 */
    @TableField("last_batch_id")
    private Long lastBatchId;

    /** 最后推进的表达式哈希 */
    @TableField("expr_hash")
    private String exprHash;
}
