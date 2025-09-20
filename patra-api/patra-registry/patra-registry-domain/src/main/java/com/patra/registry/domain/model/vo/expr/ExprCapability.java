package com.patra.registry.domain.model.vo.expr;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 表 {@code reg_prov_expr_capability} 对应的领域值对象。
 */
public record ExprCapability(
        Long id,
        Long provenanceId,
        String scopeCode,
        String taskType,
        String taskTypeKey,
        String fieldKey,
        Instant effectiveFrom,
        Instant effectiveTo,
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
        String tokenValuePattern
) {
    public ExprCapability(Long id,
                          Long provenanceId,
                          String scopeCode,
                          String taskType,
                          String taskTypeKey,
                          String fieldKey,
                          Instant effectiveFrom,
                          Instant effectiveTo,
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
                          String tokenValuePattern) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Capability id must be positive");
        }
        if (provenanceId == null || provenanceId <= 0) {
            throw new IllegalArgumentException("Provenance id must be positive");
        }
        if (scopeCode == null || scopeCode.isBlank()) {
            throw new IllegalArgumentException("Scope code cannot be blank");
        }
        if (fieldKey == null || fieldKey.isBlank()) {
            throw new IllegalArgumentException("Field key cannot be blank");
        }
        if (effectiveFrom == null) {
            throw new IllegalArgumentException("Effective from cannot be null");
        }
        if (rangeKindCode == null || rangeKindCode.isBlank()) {
            throw new IllegalArgumentException("Range kind code cannot be blank");
        }

        this.id = id;
        this.provenanceId = provenanceId;
        this.scopeCode = scopeCode.trim();
        this.taskType = taskType != null ? taskType.trim() : null;
        this.taskTypeKey = taskTypeKey != null ? taskTypeKey.trim() : "ALL";
        this.fieldKey = fieldKey.trim();
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.opsJson = opsJson;
        this.negatableOpsJson = negatableOpsJson;
        this.supportsNot = supportsNot;
        this.termMatchesJson = termMatchesJson;
        this.termCaseSensitiveAllowed = termCaseSensitiveAllowed;
        this.termAllowBlank = termAllowBlank;
        this.termMinLength = termMinLength;
        this.termMaxLength = termMaxLength;
        this.termPattern = termPattern != null ? termPattern.trim() : null;
        this.inMaxSize = inMaxSize;
        this.inCaseSensitiveAllowed = inCaseSensitiveAllowed;
        this.rangeKindCode = rangeKindCode.trim();
        this.rangeAllowOpenStart = rangeAllowOpenStart;
        this.rangeAllowOpenEnd = rangeAllowOpenEnd;
        this.rangeAllowClosedAtInfinity = rangeAllowClosedAtInfinity;
        this.dateMin = dateMin;
        this.dateMax = dateMax;
        this.datetimeMin = datetimeMin;
        this.datetimeMax = datetimeMax;
        this.numberMin = numberMin;
        this.numberMax = numberMax;
        this.existsSupported = existsSupported;
        this.tokenKindsJson = tokenKindsJson;
        this.tokenValuePattern = tokenValuePattern != null ? tokenValuePattern.trim() : null;
    }

    /** 判断当前能力是否在指定时间点生效。 */
    public boolean isEffectiveAt(Instant instant) {
        if (instant == null) {
            throw new IllegalArgumentException("Instant cannot be null");
        }
        boolean afterStart = !instant.isBefore(effectiveFrom);
        boolean beforeEnd = effectiveTo == null || instant.isBefore(effectiveTo);
        return afterStart && beforeEnd;
    }
}
