package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p><b>Plan blueprint DO</b> — table: <code>ing_plan</code></p>
 * <p>Represents a single ingestion batch blueprint, capturing provenance configuration,
 * expression prototype, and slice strategy.</p>
 * <p>Notes:
 * <ul>
 *   <li><code>plan_key</code> is human-readable and idempotent (UK: uk_plan_key).</li>
 *   <li><code>expr_proto_snapshot</code> and <code>provenance_config_snapshot</code> are stored as JSON snapshots for replay and comparison.</li>
 *   <li><code>slice_strategy_code</code> + <code>slice_params</code> determine how child slices are derived.</li>
 *   <li><code>window_spec</code> stores window boundaries as JSON; supports multiple strategies (TIME/ID_RANGE/CURSOR_LANDMARK/VOLUME_BUDGET/SINGLE).</li>
 * </ul>
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ing_plan", autoResultMap = true)
public class PlanDO extends BaseDO {

    /** Related schedule instance ID. */
    @TableField("schedule_instance_id")
    private Long scheduleInstanceId;

    /** External idempotency key (human-readable). */
    @TableField("plan_key")
    private String planKey;

    /** Provenance code redundancy (for grouping by provenance). */
    @TableField("provenance_code")
    private String provenanceCode;

    /** Operation type code (DICT: ing_operation). */
    @TableField("operation_code")
    private String operationCode;

    /** Expression prototype hash (normalized AST fingerprint). */
    @TableField("expr_proto_hash")
    private String exprProtoHash;

    /** Expression prototype snapshot (JSON AST, without local conditions). */
    @TableField(value = "expr_proto_snapshot", typeHandler = JacksonTypeHandler.class)
    private JsonNode exprProtoSnapshot;

    /** Provenance configuration snapshot (JSON, runtime-invariant parameters). */
    @TableField(value = "provenance_config_snapshot", typeHandler = JacksonTypeHandler.class)
    private JsonNode provenanceConfigSnapshot;

    /** Hash of provenance configuration snapshot (for change detection). */
    @TableField("provenance_config_hash")
    private String provenanceConfigHash;

    /** Slice strategy code (TIME/ID_RANGE/CURSOR, etc.). */
    @TableField("slice_strategy_code")
    private String sliceStrategyCode;

    /** Slice parameters snapshot (JSON; strategy-specific details). */
    @TableField(value = "slice_params", typeHandler = JacksonTypeHandler.class)
    private JsonNode sliceParams;

    /** Window boundary spec (JSON; schema varies by slice_strategy_code). */
    @TableField(value = "window_spec", typeHandler = JacksonTypeHandler.class)
    private JsonNode windowSpec;

    /** Status code (DICT: ing_plan_status). */
    @TableField("status_code")
    private String statusCode;
}
