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
 * 数据表 {@code reg_prov_window_offset_cfg} 对应的实体。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("reg_prov_window_offset_cfg")
public class RegProvWindowOffsetCfgDO extends BaseDO {

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

    @TableField("window_mode_code")
    private String windowModeCode;

    @TableField("window_size_value")
    private Integer windowSizeValue;

    @TableField("window_size_unit_code")
    private String windowSizeUnitCode;

    @TableField("calendar_align_to")
    private String calendarAlignTo;

    @TableField("lookback_value")
    private Integer lookbackValue;

    @TableField("lookback_unit_code")
    private String lookbackUnitCode;

    @TableField("overlap_value")
    private Integer overlapValue;

    @TableField("overlap_unit_code")
    private String overlapUnitCode;

    @TableField("watermark_lag_seconds")
    private Integer watermarkLagSeconds;

    @TableField("offset_type_code")
    private String offsetTypeCode;

    @TableField("offset_field_name")
    private String offsetFieldName;

    @TableField("offset_date_format")
    private String offsetDateFormat;

    @TableField("default_date_field_name")
    private String defaultDateFieldName;

    @TableField("max_ids_per_window")
    private Integer maxIdsPerWindow;

    @TableField("max_window_span_seconds")
    private Integer maxWindowSpanSeconds;

    @TableField("task_type_key")
    private String taskTypeKey;

    @TableField("lifecycle_status_code")
    private String lifecycleStatusCode;
}
