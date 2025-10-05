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
 * Persistence entity mapped to {@code reg_prov_pagination_cfg}.
 * <p>Stores pagination or cursor extraction parameters for a specific
 * provenance and operation type.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("reg_prov_pagination_cfg")
public class RegProvPaginationCfgDO extends BaseDO {

    /**
     * Foreign key referencing {@code reg_provenance.id}.
     */
    @TableField("provenance_id")
    private Long provenanceId;

    /**
     * Operation type discriminator controlling scope of the configuration.
     */
    @TableField("operation_type")
    private String operationType;

    /**
     * Inclusive timestamp marking when the pagination rule becomes effective.
     */
    @TableField("effective_from")
    private Instant effectiveFrom;

    /**
     * Exclusive timestamp marking when the pagination rule expires.
     */
    @TableField("effective_to")
    private Instant effectiveTo;

    /**
     * Code describing the pagination strategy (PAGE_NUMBER/CURSOR/TOKEN/SCROLL).
     */
    @TableField("pagination_mode_code")
    private String paginationModeCode;

    /**
     * Default page size to use when issuing requests.
     */
    @TableField("page_size_value")
    private Integer pageSizeValue;

    /**
     * Maximum number of pages to advance during a single execution.
     */
    @TableField("max_pages_per_execution")
    private Integer maxPagesPerExecution;

    /**
     * Name of the request parameter that controls server-side sorting.
     */
    @TableField("sort_field_param_name")
    private String sortFieldParamName;

    /**
     * Sorting direction flag ({@code 1} for ascending, {@code 0} for descending).
     */
    @TableField("sorting_direction")
    private Integer sortingDirection;

    /**
     * Lifecycle status code, typically {@code ACTIVE} for valid records.
     */
    @TableField("lifecycle_status_code")
    private String lifecycleStatusCode;
}
