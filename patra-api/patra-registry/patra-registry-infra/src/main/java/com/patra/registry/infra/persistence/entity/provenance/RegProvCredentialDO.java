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
 * Persistence entity mapped to {@code reg_prov_credential}.
 * <p>Stores credential material (API keys, basic auth, OAuth) for a provenance
 * and operation combination.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("reg_prov_credential")
public class RegProvCredentialDO extends BaseDO {

    /**
     * Foreign key referencing {@code reg_provenance.id}.
     */
    @TableField("provenance_id")
    private Long provenanceId;

    /**
     * Operation type discriminator that scopes credential usage.
     */
    @TableField("operation_type")
    private String operationType;

    /**
     * Logical credential name exposed to the application layer.
     */
    @TableField("credential_name")
    private String credentialName;

    /**
     * Authentication type (API_KEY/BASIC/OAUTH/etc.).
     */
    @TableField("auth_type")
    private String authType;

    /**
     * Inbound storage location for the secret (e.g., NACOS/VAULT/ENV).
     */
    @TableField("inbound_location_code")
    private String inboundLocationCode;

    /**
     * Name of the field that should receive the credential value.
     */
    @TableField("credential_field_name")
    private String credentialFieldName;

    /**
     * Optional prefix prepended to the credential value (e.g., {@code Bearer }).
     */
    @TableField("credential_value_prefix")
    private String credentialValuePrefix;

    /**
     * Reference key used to resolve the actual credential value from secure storage.
     */
    @TableField("credential_value_ref")
    private String credentialValueRef;

    /**
     * Reference key pointing to the username for BASIC authentication.
     */
    @TableField("basic_username_ref")
    private String basicUsernameRef;

    /**
     * Reference key pointing to the password for BASIC authentication.
     */
    @TableField("basic_password_ref")
    private String basicPasswordRef;

    /**
     * OAuth token endpoint URL when {@code auth_type} requires OAuth flows.
     */
    @TableField("oauth_token_url")
    private String oauthTokenUrl;

    /**
     * Reference key for the OAuth client identifier.
     */
    @TableField("oauth_client_id_ref")
    private String oauthClientIdRef;

    /**
     * Reference key for the OAuth client secret.
     */
    @TableField("oauth_client_secret_ref")
    private String oauthClientSecretRef;

    /**
     * Requested OAuth scopes; {@code null} adopts provider defaults.
     */
    @TableField("oauth_scope")
    private String oauthScope;

    /**
     * OAuth audience parameter used when the provider enforces audience checks.
     */
    @TableField("oauth_audience")
    private String oauthAudience;

    /**
     * Extensible JSON payload for provider-specific options.
     */
    @TableField("extra_json")
    private String extraJson;

    /**
     * Inclusive timestamp when the credential becomes active.
     */
    @TableField("effective_from")
    private Instant effectiveFrom;

    /**
     * Exclusive timestamp indicating when the credential expires.
     */
    @TableField("effective_to")
    private Instant effectiveTo;

    /**
     * Flag marking whether this credential is the preferred default.
     */
    @TableField("is_default_preferred")
    private Boolean isDefaultPreferred;

    /**
     * Normalized operation type key (defaults to {@code ALL}).
     */
    @TableField("operation_type_key")
    private String operationTypeKey;

    /**
     * Reserved field for future preference ordering.
     */
    @TableField("preferred_1")
    private String preferred1;

    /**
     * Lifecycle status code (e.g., ACTIVE/INACTIVE) for the credential row.
     */
    @TableField("lifecycle_status_code")
    private String lifecycleStatusCode;
}
