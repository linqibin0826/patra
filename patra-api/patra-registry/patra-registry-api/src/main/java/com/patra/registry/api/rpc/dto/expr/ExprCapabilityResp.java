package com.patra.registry.api.rpc.dto.expr;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 字段能力响应 DTO。
 */
public record ExprCapabilityResp(
        Long provenanceId,
        String scopeCode,
        String taskType,
        String fieldKey,
        String opsJson,
        String negatableOpsJson,
        boolean supportsNot,
        String termMatchesJson,
        boolean termCaseSensitiveAllowed,
        boolean termAllowBlank,
        int termMinLength,
        int termMaxLength,
        String termPattern,
        int inMaxSize,
        boolean inCaseSensitiveAllowed,
        String rangeKindCode,
        boolean rangeAllowOpenStart,
        boolean rangeAllowOpenEnd,
        boolean rangeAllowClosedAtInfinity,
        LocalDate dateMin,
        LocalDate dateMax,
        Instant datetimeMin,
        Instant datetimeMax,
        BigDecimal numberMin,
        BigDecimal numberMax,
        boolean existsSupported,
        String tokenKindsJson,
        String tokenValuePattern,
        Instant effectiveFrom,
        Instant effectiveTo
) {
}
