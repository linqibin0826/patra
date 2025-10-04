package com.patra.registry.domain.model.read.provenance;

import com.patra.registry.domain.exception.DomainValidationException;

import java.time.Instant;

/**
 * 鉴权凭证查询视图。
 */
public record CredentialQuery(
        Long id,
        Long provenanceId,
        String taskType,
        String taskTypeKey,
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
            throw new DomainValidationException("Credential id must be positive");
        }
        if (provenanceId == null || provenanceId <= 0) {
            throw new DomainValidationException("Provenance id must be positive");
        }
        if (credentialName == null || credentialName.isBlank()) {
            throw new DomainValidationException("Credential name cannot be blank");
        }
        if (authType == null || authType.isBlank()) {
            throw new DomainValidationException("Auth type cannot be blank");
        }
        if (inboundLocationCode == null || inboundLocationCode.isBlank()) {
            throw new DomainValidationException("Inbound location code cannot be blank");
        }
        if (lifecycleStatusCode == null || lifecycleStatusCode.isBlank()) {
            throw new DomainValidationException("Lifecycle status code cannot be blank");
        }
        if (effectiveFrom == null) {
            throw new DomainValidationException("Effective from cannot be null");
        }
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
    // extraJson 原值保持（JSON 结构外部消费时再解析）
        lifecycleStatusCode = lifecycleStatusCode.trim();
    }
}
