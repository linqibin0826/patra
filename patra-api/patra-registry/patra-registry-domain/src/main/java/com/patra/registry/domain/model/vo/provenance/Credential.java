package com.patra.registry.domain.model.vo.provenance;

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
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Credential id must be positive");
        }
        if (provenanceId == null || provenanceId <= 0) {
            throw new IllegalArgumentException("Provenance id must be positive");
        }
        if (scopeCode == null || scopeCode.isBlank()) {
            throw new IllegalArgumentException("Scope code cannot be blank");
        }
        if (credentialName == null || credentialName.isBlank()) {
            throw new IllegalArgumentException("Credential name cannot be blank");
        }
        if (authType == null || authType.isBlank()) {
            throw new IllegalArgumentException("Auth type cannot be blank");
        }
        if (inboundLocationCode == null || inboundLocationCode.isBlank()) {
            throw new IllegalArgumentException("Inbound location code cannot be blank");
        }
        if (lifecycleStatusCode == null || lifecycleStatusCode.isBlank()) {
            throw new IllegalArgumentException("Lifecycle status code cannot be blank");
        }
        if (effectiveFrom == null) {
            throw new IllegalArgumentException("Effective from cannot be null");
        }

        this.id = id;
        this.provenanceId = provenanceId;
        this.scopeCode = scopeCode.trim();
        this.taskType = taskType != null ? taskType.trim() : null;
        this.taskTypeKey = taskTypeKey != null ? taskTypeKey.trim() : "ALL";
        this.endpointId = endpointId;
        this.credentialName = credentialName.trim();
        this.authType = authType.trim();
        this.inboundLocationCode = inboundLocationCode.trim();
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
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.defaultPreferred = defaultPreferred;
        this.lifecycleStatusCode = lifecycleStatusCode.trim();
    }

    public boolean isDefaultPreferred() {
        return defaultPreferred;
    }
}
