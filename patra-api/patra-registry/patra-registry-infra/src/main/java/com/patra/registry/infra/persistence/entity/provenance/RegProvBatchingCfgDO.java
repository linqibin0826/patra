package com.patra.registry.infra.persistence.entity.provenance;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Persistence entity mapped to {@code reg_prov_batching_cfg}.
 * <p>Represents per-provenance batching settings that control how IDs are grouped
 * into downstream requests for a specific operation type.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("reg_prov_batching_cfg")
public class RegProvBatchingCfgDO extends BaseDO {

    /**
     * Foreign key referencing {@code reg_provenance.id}.
     */
    @TableField("provenance_id")
    private Long provenanceId;

    /**
     * Operation type discriminator (e.g., ALL/HARVEST/UPDATE/BACKFILL).
     */
    @TableField("operation_type")
    private String operationType;

    /**
     * Inclusive effective start timestamp for this configuration slice.
     */
    @TableField("effective_from")
    private Instant effectiveFrom;

    /**
     * Exclusive effective end timestamp; {@code null} denotes an open-ended slice.
     */
    @TableField("effective_to")
    private Instant effectiveTo;

    /**
     * Suggested maximum number of IDs to fetch per detail batch request.
     */
    @TableField("detail_fetch_batch_size")
    private Integer detailFetchBatchSize;

    /**
     * HTTP parameter name carrying the batched ID list.
     */
    @TableField("ids_param_name")
    private String idsParamName;

    /**
     * Delimiter used when joining IDs into a single parameter value.
     */
    @TableField("ids_join_delimiter")
    private String idsJoinDelimiter;

    /**
     * Hard cap of IDs allowed per downstream request.
     */
    @TableField("max_ids_per_request")
    private Integer maxIdsPerRequest;

    /**
     * Normalized operation type key (defaults to {@code ALL}).
     */
    @TableField("operation_type_key")
    private String operationTypeKey;

    /**
     * Lifecycle status code, typically {@code ACTIVE} for effective rows.
     */
    @TableField("lifecycle_status_code")
    private String lifecycleStatusCode;
}
