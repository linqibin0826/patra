package com.patra.registry.infra.persistence.entity.provenance;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO.BaseDO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * 数据表 {@code reg_prov_pagination_cfg} 对应的实体。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("reg_prov_pagination_cfg")
public class RegProvPaginationCfgDO extends BaseDO {

    @TableField("provenance_id")
    private Long provenanceId;

    @TableField("scope_code")
    private String scopeCode;

    @TableField("task_type")
    private String taskType;

    @TableField("effective_from")
    private Instant effectiveFrom;

    @TableField("effective_to")
    private Instant effectiveTo;

    @TableField("pagination_mode_code")
    private String paginationModeCode;

    @TableField("page_size_value")
    private Integer pageSizeValue;

    @TableField("max_pages_per_execution")
    private Integer maxPagesPerExecution;

    @TableField("page_number_param_name")
    private String pageNumberParamName;

    @TableField("page_size_param_name")
    private String pageSizeParamName;

    @TableField("start_page_number")
    private Integer startPageNumber;

    @TableField("sort_field_param_name")
    private String sortFieldParamName;

    @TableField("sort_direction")
    private String sortDirection;

    @TableField("cursor_param_name")
    private String cursorParamName;

    @TableField("initial_cursor_value")
    private String initialCursorValue;

    @TableField("next_cursor_jsonpath")
    private String nextCursorJsonpath;

    @TableField("has_more_jsonpath")
    private String hasMoreJsonpath;

    @TableField("total_count_jsonpath")
    private String totalCountJsonpath;

    @TableField("next_cursor_xpath")
    private String nextCursorXpath;

    @TableField("has_more_xpath")
    private String hasMoreXpath;

    @TableField("total_count_xpath")
    private String totalCountXpath;

    @TableField("task_type_key")
    private String taskTypeKey;

    @TableField("lifecycle_status_code")
    private String lifecycleStatusCode;
}
