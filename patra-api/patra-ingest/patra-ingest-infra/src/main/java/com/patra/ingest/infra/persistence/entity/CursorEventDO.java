package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO.BaseDO;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = true)
@TableName("ing_cursor_event")
/**
 * 游标事件表 (ing_cursor_event)
 * 记录每次游标推进的前后值变化及来源上下文，支持审计与回放。
 */
public class CursorEventDO extends BaseDO {
    /** 来源代码 */
    @TableField("provenance_code") private String provenanceCode;
    /** 操作代码 */
    @TableField("operation_code") private String operationCode;
    /** 游标逻辑键 */
    @TableField("cursor_key") private String cursorKey;
    /** 命名空间作用域 */
    @TableField("namespace_scope_code") private String namespaceScopeCode;
    /** 命名空间键 */
    @TableField("namespace_key") private String namespaceKey;
    /** 游标类型 */
    @TableField("cursor_type_code") private String cursorTypeCode;
    /** 旧值（原始表示） */
    @TableField("prev_value") private String prevValue;
    /** 新值（原始表示） */
    @TableField("new_value") private String newValue;
    /** 当次推进观察到的最大值 */
    @TableField("observed_max_value") private String observedMaxValue;
    /** 旧值标准化时间 */
    @TableField("prev_instant") private java.time.Instant prevInstant;
    /** 新值标准化时间 */
    @TableField("new_instant") private java.time.Instant newInstant;
    /** 旧值标准化数值 */
    @TableField("prev_numeric") private java.math.BigDecimal prevNumeric;
    /** 新值标准化数值 */
    @TableField("new_numeric") private java.math.BigDecimal newNumeric;
    /** 推进覆盖窗口起（如果与窗口语义相关） */
    @TableField("window_from") private java.time.Instant windowFrom;
    /** 推进覆盖窗口止 */
    @TableField("window_to") private java.time.Instant windowTo;
    /** 方向（FORWARD/REWIND 等） */
    @TableField("direction_code") private String directionCode;
    /** 幂等键（防重复写同一事件） */
    @TableField("idempotent_key") private String idempotentKey;
    /** 关联调度实例 */
    @TableField("schedule_instance_id") private Long scheduleInstanceId;
    /** 关联计划 */
    @TableField("plan_id") private Long planId;
    /** 关联切片 */
    @TableField("slice_id") private Long sliceId;
    /** 关联任务 */
    @TableField("task_id") private Long taskId;
    /** 关联运行 */
    @TableField("run_id") private Long runId;
    /** 关联批次 */
    @TableField("batch_id") private Long batchId;
    /** 表达式 hash */
    @TableField("expr_hash") private String exprHash;
}
