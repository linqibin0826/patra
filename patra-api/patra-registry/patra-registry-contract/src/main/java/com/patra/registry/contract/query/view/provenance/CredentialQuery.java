package com.patra.registry.contract.query.view.provenance;

import java.time.Instant;

/**
 * 鉴权凭证查询视图。
 */
public record CredentialQuery(
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
    public CredentialQuery {
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
        scopeCode = scopeCode.trim();
        taskType = taskType != null ? taskType.trim() : null;
        taskTypeKey = taskTypeKey != null ? taskTypeKey.trim() : "ALL";
        credentialName = credentialName.trim();
        authType = authType.trim();
        inboundLocationCode = inboundLocationCode.trim();
        credentialFieldName = credentialFieldName != null ? credentialFieldName.trim() : null;
        credentialValuePrefix = credentialValuePrefix != null ? credentialValuePrefix.trim() : null;
        credentialValueRef = credentialValueRef != null ? credentialValueRef.trim() : null;
        basicUsernameRef = basicUsernameRef != null ? basicUsernameRef.trim() : null;
        basicPasswordRef = basicPasswordRef != null ? basicPasswordRef.trim() : null;
        oauthTokenUrl = oauthTokenUrl != null ? oauthTokenUrl.trim() : null;
        oauthClientIdRef = oauthClientIdRef != null ? oauthClientIdRef.trim() : null;
        oauthClientSecretRef = oauthClientSecretRef != null ? oauthClientSecretRef.trim() : null;
        oauthScope = oauthScope != null ? oauthScope.trim() : null;
        oauthAudience = oauthAudience != null ? oauthAudience.trim() : null;
        extraJson = extraJson;
        lifecycleStatusCode = lifecycleStatusCode.trim();
    }
}
