package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * <p><b>Generic cursor DO</b> — table: <code>ing_cursor</code></p>
 * <p>Maintains the current watermark for provenance + operation + namespace, supporting
 * three cursor types: time / numeric / token.</p>
 * <p>Notes:
 * <ul>
 *   <li><code>namespace_scope_code</code> + <code>namespace_key</code> distinguish namespaces (GLOBAL / EXPR / CUSTOM).</li>
 *   <li><code>normalized_instant</code>/<code>normalized_numeric</code> normalize values to enable ordering and range queries.</li>
 *   <li>Redundant lineage fields (schedule/plan/slice/task/run/batch) allow quick backtracking.</li>
 *   <li>Recommended unique key: (provenance_code, operation_code, cursor_key, namespace_scope_code, namespace_key).</li>
 * </ul>
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ing_cursor")
public class CursorDO extends BaseDO {

    /** Provenance code (consistent with registry). */
    @TableField("provenance_code")
    private String provenanceCode;

    /** Operation type code. */
    @TableField("operation_code")
    private String operationCode;

    /** Cursor key (e.g., updated_at / seq_id / cursor_token). */
    @TableField("cursor_key")
    private String cursorKey;

    /** Namespace scope (GLOBAL/EXPR/CUSTOM). */
    @TableField("namespace_scope_code")
    private String namespaceScopeCode;

    /** Namespace key (expr_hash or a custom hash). */
    @TableField("namespace_key")
    private String namespaceKey;

    /** Cursor type (DICT: ing_cursor_type). */
    @TableField("cursor_type_code")
    private String cursorTypeCode;

    /** Current raw cursor value. */
    @TableField("cursor_value")
    private String cursorValue;

    /** Maximum observed value. */
    @TableField("observed_max_value")
    private String observedMaxValue;

    /** Normalized time value (when type=TIME). */
    @TableField("normalized_instant")
    private Instant normalizedInstant;

    /** Normalized numeric value (when type=ID). */
    @TableField("normalized_numeric")
    private BigDecimal normalizedNumeric;

    /** Most recent schedule instance advancing this cursor. */
    @TableField("schedule_instance_id")
    private Long scheduleInstanceId;

    /** Most recent associated plan. */
    @TableField("plan_id")
    private Long planId;

    /** Most recent associated slice. */
    @TableField("slice_id")
    private Long sliceId;

    /** Most recent associated task. */
    @TableField("task_id")
    private Long taskId;

    /** Most recent associated run. */
    @TableField("last_run_id")
    private Long lastRunId;

    /** Most recent associated batch. */
    @TableField("last_batch_id")
    private Long lastBatchId;

    /** Expression hash used in the latest advancement. */
    @TableField("expr_hash")
    private String exprHash;
}
