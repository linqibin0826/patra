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
 * 数据表 {@code reg_prov_credential} 对应的实体。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("reg_prov_credential")
public class RegProvCredentialDO extends BaseDO {

    @TableField("provenance_id")
    private Long provenanceId;

    @TableField("scope_code")
    private String scopeCode;

    @TableField("task_type")
    private String taskType;

    @TableField("endpoint_id")
    private Long endpointId;

    @TableField("credential_name")
    private String credentialName;

    @TableField("auth_type")
    private String authType;

    @TableField("inbound_location_code")
    private String inboundLocationCode;

    @TableField("credential_field_name")
    private String credentialFieldName;

    @TableField("credential_value_prefix")
    private String credentialValuePrefix;

    @TableField("credential_value_ref")
    private String credentialValueRef;

    @TableField("basic_username_ref")
    private String basicUsernameRef;

    @TableField("basic_password_ref")
    private String basicPasswordRef;

    @TableField("oauth_token_url")
    private String oauthTokenUrl;

    @TableField("oauth_client_id_ref")
    private String oauthClientIdRef;

    @TableField("oauth_client_secret_ref")
    private String oauthClientSecretRef;

    @TableField("oauth_scope")
    private String oauthScope;

    @TableField("oauth_audience")
    private String oauthAudience;

    @TableField("extra_json")
    private String extraJson;

    @TableField("effective_from")
    private Instant effectiveFrom;

    @TableField("effective_to")
    private Instant effectiveTo;

    @TableField("is_default_preferred")
    private Boolean isDefaultPreferred;

    @TableField("task_type_key")
    private String taskTypeKey;

    @TableField("endpoint_id_key")
    private Long endpointIdKey;

    @TableField("preferred_1")
    private String preferred1;

    @TableField("lifecycle_status_code")
    private String lifecycleStatusCode;
}
