package com.patra.registry.api.rpc.dto.provenance;

import java.time.Instant;

/**
 * HTTP 策略响应 DTO。
 */
public record HttpConfigResp(
        Long id,
        Long provenanceId,
        String scopeCode,
        String taskType,
        String taskTypeKey,
        Instant effectiveFrom,
        Instant effectiveTo,
        String baseUrlOverride,
        String defaultHeadersJson,
        Integer timeoutConnectMillis,
        Integer timeoutReadMillis,
        Integer timeoutTotalMillis,
        boolean tlsVerifyEnabled,
        String proxyUrlValue,
        boolean acceptCompressEnabled,
        boolean preferHttp2Enabled,
        String retryAfterPolicyCode,
        Integer retryAfterCapMillis,
        String idempotencyHeaderName,
        Integer idempotencyTtlSeconds
) {
}
