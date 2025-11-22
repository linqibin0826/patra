package com.patra.registry.api.dto.provenance;

import java.time.Instant;

/// 速率限制和并发配置。
///
/// 字段说明:
///
/// @author linqibin
/// @since 0.1.0
public record RateLimitConfigResp(
    Long id,
    Long provenanceId,
    String operationType,
    Instant effectiveFrom,
    Instant effectiveTo,
    Integer maxConcurrentRequests,
    Integer perCredentialQpsLimit) {}
