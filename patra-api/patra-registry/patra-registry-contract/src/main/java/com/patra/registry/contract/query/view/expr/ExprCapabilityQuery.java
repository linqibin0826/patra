package com.patra.registry.contract.query.view.expr;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 字段能力查询视图。
 */
public record ExprCapabilityQuery(
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
    public ExprCapabilityQuery {
        if (provenanceId == null || provenanceId <= 0) {
            throw new IllegalArgumentException("Provenance id must be positive");
        }
        if (scopeCode == null || scopeCode.isBlank()) {
            throw new IllegalArgumentException("Scope code cannot be blank");
        }
        if (fieldKey == null || fieldKey.isBlank()) {
            throw new IllegalArgumentException("Field key cannot be blank");
        }
        if (rangeKindCode == null || rangeKindCode.isBlank()) {
            throw new IllegalArgumentException("Range kind code cannot be blank");
        }
        if (effectiveFrom == null) {
            throw new IllegalArgumentException("Effective from cannot be null");
        }
        scopeCode = scopeCode.trim();
        taskType = taskType != null ? taskType.trim() : null;
        fieldKey = fieldKey.trim();
        rangeKindCode = rangeKindCode.trim();
        termPattern = termPattern != null ? termPattern.trim() : null;
        tokenValuePattern = tokenValuePattern != null ? tokenValuePattern.trim() : null;
    }
}
