package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * <p><b>游标推进事件 DO</b> —— 映射表：<code>ing_cursor_event</code></p>
 * <p>语义：Append-only 审计事件，记录游标从旧值到新值的每次推进，支持回放与全链路追踪。</p>
 * <p>要点：
 * <ul>
 *   <li><code>idempotent_key</code> 唯一，防止重复写入同一事件。</li>
 *   <li>同时持久化时间/数值归一值，便于跨类型排序与统计。</li>
 *   <li>链路冗余字段串联 schedule → plan → slice → task → run → batch。</li>
 * </ul>
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ing_cursor_event")
public class CursorEventDO extends BaseDO {

    /** 来源代码 */
    @TableField("provenance_code")
    private String provenanceCode;

    /** 操作类型编码 */
    @TableField("operation_code")
    private String operationCode;

    /** 游标逻辑键 */
    @TableField("cursor_key")
    private String cursorKey;

    /** 命名空间作用域 */
    @TableField("namespace_scope_code")
    private String namespaceScopeCode;

    /** 命名空间键 */
    @TableField("namespace_key")
    private String namespaceKey;

    /** 游标类型（DICT：ing_cursor_type） */
    @TableField("cursor_type_code")
    private String cursorTypeCode;

    /** 推进前的原始值 */
    @TableField("prev_value")
    private String prevValue;

    /** 推进后的原始值 */
    @TableField("new_value")
    private String newValue;

    /** 推进过程观测到的最大值 */
    @TableField("observed_max_value")
    private String observedMaxValue;

    /** 推进前的归一化时间 */
    @TableField("prev_instant")
    private Instant prevInstant;

    /** 推进后的归一化时间 */
    @TableField("new_instant")
    private Instant newInstant;

    /** 推进前的归一化数值 */
    @TableField("prev_numeric")
    private BigDecimal prevNumeric;

    /** 推进后的归一化数值 */
    @TableField("new_numeric")
    private BigDecimal newNumeric;

    /** 覆盖窗口起点（UTC，含） */
    @TableField("window_from")
    private Instant windowFrom;

    /** 覆盖窗口终点（UTC，不含） */
    @TableField("window_to")
    private Instant windowTo;

    /** 推进方向（FORWARD/BACKFILL） */
    @TableField("direction_code")
    private String directionCode;

    /** 事件幂等键（UK：uk_cur_evt_idem） */
    @TableField("idempotent_key")
    private String idempotentKey;

    /** 最近推进关联的调度实例 */
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
    @TableField("run_id")
    private Long runId;

    /** 最近推进关联的批次 */
    @TableField("batch_id")
    private Long batchId;

    /** 推进时的表达式哈希 */
    @TableField("expr_hash")
    private String exprHash;
}
