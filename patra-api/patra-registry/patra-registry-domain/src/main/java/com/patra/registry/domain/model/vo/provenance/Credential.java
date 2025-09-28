package com.patra.registry.domain.model.vo.provenance;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;

/**
 * {@code reg_prov_credential} 的领域值对象。
 */
public record Credential(
        Long id,
        Long provenanceId,
        String scopeCode,
        String taskType,
        String taskTypeKey,
        Long endpointId,
        String credentialName,
        String authType,
        String inboundLocationCode,
        String credentialFieldName,
        String credentialValuePrefix,
        String credentialValueRef,
        String basicUsernameRef,
        String basicPasswordRef,
        String oauthTokenUrl,
        String oauthClientIdRef,
        String oauthClientSecretRef,
        String oauthScope,
        String oauthAudience,
        String extraJson,
        Instant effectiveFrom,
        Instant effectiveTo,
        boolean defaultPreferred,
        String lifecycleStatusCode
) {
    public Credential(Long id,
                      Long provenanceId,
                      String scopeCode,
                      String taskType,
                      String taskTypeKey,
                      Long endpointId,
                      String credentialName,
                      String authType,
                      String inboundLocationCode,
                      String credentialFieldName,
                      String credentialValuePrefix,
                      String credentialValueRef,
                      String basicUsernameRef,
                      String basicPasswordRef,
                      String oauthTokenUrl,
                      String oauthClientIdRef,
                      String oauthClientSecretRef,
                      String oauthScope,
                      String oauthAudience,
                      String extraJson,
                      Instant effectiveFrom,
                      Instant effectiveTo,
                      boolean defaultPreferred,
                      String lifecycleStatusCode) {
        DomainValidationException.positive(id, "Credential id");
        DomainValidationException.positive(provenanceId, "Provenance id");
        String scopeTrimmed = DomainValidationException.notBlank(scopeCode, "Scope code");
        String nameTrimmed = DomainValidationException.notBlank(credentialName, "Credential name");
        String authTypeTrimmed = DomainValidationException.notBlank(authType, "Auth type");
        String inboundLocTrimmed = DomainValidationException.notBlank(inboundLocationCode, "Inbound location code");
        String lifecycleTrimmed = DomainValidationException.notBlank(lifecycleStatusCode, "Lifecycle status code");
        DomainValidationException.nonNull(effectiveFrom, "Effective from");

        this.id = id; // 已验证
        this.provenanceId = provenanceId; // 已验证
        this.scopeCode = scopeTrimmed;
        this.taskType = taskType != null ? taskType.trim() : null;
        this.taskTypeKey = taskTypeKey != null ? taskTypeKey.trim() : "ALL";
        this.endpointId = endpointId;
        this.credentialName = nameTrimmed;
        this.authType = authTypeTrimmed;
        this.inboundLocationCode = inboundLocTrimmed;
        this.credentialFieldName = credentialFieldName != null ? credentialFieldName.trim() : null;
        this.credentialValuePrefix = credentialValuePrefix != null ? credentialValuePrefix.trim() : null;
        this.credentialValueRef = credentialValueRef != null ? credentialValueRef.trim() : null;
        this.basicUsernameRef = basicUsernameRef != null ? basicUsernameRef.trim() : null;
        this.basicPasswordRef = basicPasswordRef != null ? basicPasswordRef.trim() : null;
        this.oauthTokenUrl = oauthTokenUrl != null ? oauthTokenUrl.trim() : null;
        this.oauthClientIdRef = oauthClientIdRef != null ? oauthClientIdRef.trim() : null;
        this.oauthClientSecretRef = oauthClientSecretRef != null ? oauthClientSecretRef.trim() : null;
        this.oauthScope = oauthScope != null ? oauthScope.trim() : null;
        this.oauthAudience = oauthAudience != null ? oauthAudience.trim() : null;
        this.extraJson = extraJson;
        this.effectiveFrom = effectiveFrom; // 非 null 验证
        this.effectiveTo = effectiveTo;
        this.defaultPreferred = defaultPreferred;
        this.lifecycleStatusCode = lifecycleTrimmed;
    }

    public boolean isDefaultPreferred() {
        return defaultPreferred;
    }
}
