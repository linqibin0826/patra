package com.patra.registry.domain.model.read.expr;

import com.patra.registry.domain.exception.DomainValidationException;
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
        DomainValidationException.positive(provenanceId, "Provenance id");
        scopeCode = DomainValidationException.notBlank(scopeCode, "Scope code");
        fieldKey = DomainValidationException.notBlank(fieldKey, "Field key");
        rangeKindCode = DomainValidationException.notBlank(rangeKindCode, "Range kind code");
        DomainValidationException.nonNull(effectiveFrom, "Effective from");
        taskType = taskType != null ? taskType.trim() : null;
        termPattern = termPattern != null ? termPattern.trim() : null;
        tokenValuePattern = tokenValuePattern != null ? tokenValuePattern.trim() : null;
    }
}
