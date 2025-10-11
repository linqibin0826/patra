package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p><b>Plan slice DO</b> — table: <code>ing_plan_slice</code></p>
 * <p>Represents the smallest idempotent execution unit derived from a plan by strategy. One task per slice.</p>
 * <p>Notes:
 * <ul>
 *   <li><code>slice_signature_hash</code> is a normalized hash of <code>window_spec</code> (UK: uk_slice_signature) to prevent duplicates.</li>
 *   <li><code>window_spec</code> and <code>expr_snapshot</code> are JSON ASTs stored via {@link JacksonTypeHandler}.</li>
 * </ul>
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ing_plan_slice", autoResultMap = true)
public class PlanSliceDO extends BaseDO {

    /** Associated plan id. */
    @TableField("plan_id")
    private Long planId;

    /** Redundant provenance code. */
    @TableField("provenance_code")
    private String provenanceCode;

    /** Slice sequence number (0..N). */
    @TableField("slice_no")
    private Integer sliceNo;

    /** Slice signature hash (based on normalized window_spec). */
    @TableField("slice_signature_hash")
    private String sliceSignatureHash;

    /** Window boundary spec (JSON). */
    @TableField(value = "window_spec", typeHandler = JacksonTypeHandler.class)
    private JsonNode windowSpec;

    /** Localized expression hash. */
    @TableField("expr_hash")
    private String exprHash;

    /** Localized expression snapshot (JSON AST; replayable). */
    @TableField(value = "expr_snapshot", typeHandler = JacksonTypeHandler.class)
    private JsonNode exprSnapshot;

    /** Slice status (DICT: ing_slice_status). */
    @TableField("status_code")
    private String statusCode;
}
