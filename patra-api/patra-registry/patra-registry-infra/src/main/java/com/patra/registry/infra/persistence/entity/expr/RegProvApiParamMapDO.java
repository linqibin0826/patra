package com.patra.registry.infra.persistence.entity.expr;

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
 * 数据表 {@code reg_prov_api_param_map} 对应的实体。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("reg_prov_api_param_map")
public class RegProvApiParamMapDO extends BaseDO {

    @TableField("provenance_id")
    private Long provenanceId;

    @TableField("operation_type")
    private String operationType;

    @TableField("operation_type_key")
    private String operationTypeKey;

    @TableField("lifecycle_status_code")
    private String lifecycleStatusCode;

    @TableField("operation_code")
    private String operationCode;

    @TableField("std_key")
    private String stdKey;

    @TableField("provider_param_name")
    private String providerParamName;

    @TableField("transform_code")
    private String transformCode;

    @TableField("notes")
    private String notes;

    @TableField("effective_from")
    private Instant effectiveFrom;

    @TableField("effective_to")
    private Instant effectiveTo;
}
