package com.patra.registry.api.dto.provenance;

import java.time.Instant;

/// 数据源 API 调用的基线 HTTP 配置。
/// 
/// 字段说明:
/// 
/// @author linqibin
/// @since 0.1.0
public record HttpConfigResp(
    Long id,
    Long provenanceId,
    String operationType,
    Instant effectiveFrom,
    Instant effectiveTo,
    String defaultHeadersJson,
    Integer timeoutConnectMillis,
    Integer timeoutReadMillis,
    Integer timeoutTotalMillis,
    boolean tlsVerifyEnabled,
    String proxyUrlValue,
    String retryAfterPolicyCode,
    Integer retryAfterCapMillis,
    String idempotencyHeaderName,
    Integer idempotencyTtlSeconds) {}
