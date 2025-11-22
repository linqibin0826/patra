package com.patra.registry.api.dto.provenance;

import java.time.Instant;

/// 数据源 API 调用的重试和退避配置。
/// 
/// 字段说明:
/// 
/// @author linqibin
/// @since 0.1.0
public record RetryConfigResp(
    Long id,
    Long provenanceId,
    String operationType,
    Instant effectiveFrom,
    Instant effectiveTo,
    Integer maxRetryTimes,
    String backoffPolicyTypeCode,
    Integer initialDelayMillis,
    Integer maxDelayMillis,
    Double expMultiplierValue,
    Double jitterFactorRatio,
    String retryHttpStatusJson,
    String giveupHttpStatusJson,
    boolean retryOnNetworkError,
    Integer circuitBreakThreshold,
    Integer circuitCooldownMillis) {}
