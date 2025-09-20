package com.patra.registry.api.rpc.dto.provenance;

import java.time.Instant;

/**
 * 凭证响应 DTO。
 */
public record CredentialResp(
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
}
