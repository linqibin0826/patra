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
 * 数据表 {@code reg_prov_endpoint_def} 对应的实体。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("reg_prov_endpoint_def")
public class RegProvEndpointDefDO extends BaseDO {

    @TableField("provenance_id")
    private Long provenanceId;

    @TableField("scope_code")
    private String scopeCode;

    @TableField("task_type")
    private String taskType;

    @TableField("endpoint_name")
    private String endpointName;

    @TableField("effective_from")
    private Instant effectiveFrom;

    @TableField("effective_to")
    private Instant effectiveTo;

    @TableField("endpoint_usage_code")
    private String endpointUsageCode;

    @TableField("http_method_code")
    private String httpMethodCode;

    @TableField("path_template")
    private String pathTemplate;

    @TableField("default_query_params")
    private String defaultQueryParams;

    @TableField("default_body_payload")
    private String defaultBodyPayload;

    @TableField("request_content_type")
    private String requestContentType;

    @TableField("is_auth_required")
    private Boolean isAuthRequired;

    @TableField("credential_hint_name")
    private String credentialHintName;

    @TableField("page_param_name")
    private String pageParamName;

    @TableField("page_size_param_name")
    private String pageSizeParamName;

    @TableField("cursor_param_name")
    private String cursorParamName;

    @TableField("ids_param_name")
    private String idsParamName;

    @TableField("task_type_key")
    private String taskTypeKey;

    @TableField("lifecycle_status_code")
    private String lifecycleStatusCode;
}
