package com.patra.registry.domain.model.vo.expr;

import com.patra.registry.domain.exception.DomainValidationException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Domain value object for {@code reg_prov_expr_capability}.
 *
 * <p>Declares allowed expression operators and constraints per field/key and scope.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record ExprCapability(
        Long id,
        Long provenanceId,
        String operationType,
        String operationTypeKey,
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
                          String operationType,
                          String operationTypeKey,
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
        DomainValidationException.positive(id, "Capability id");
        DomainValidationException.positive(provenanceId, "Provenance id");
        String fieldKeyTrimmed = DomainValidationException.notBlank(fieldKey, "Field key");
        DomainValidationException.nonNull(effectiveFrom, "Effective from");
        String rangeKindTrimmed = DomainValidationException.notBlank(rangeKindCode, "Range kind code");

        this.id = id; // 已验证
        this.provenanceId = provenanceId; // 已验证
        this.operationType = operationType != null ? operationType.trim() : null;
        this.operationTypeKey = operationTypeKey != null ? operationTypeKey.trim() : "ALL";
        this.fieldKey = fieldKeyTrimmed;
        this.effectiveFrom = effectiveFrom; // 非 null 已验证
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
    this.rangeKindCode = rangeKindTrimmed;
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
        DomainValidationException.nonNull(instant, "Instant");
        boolean afterStart = !instant.isBefore(effectiveFrom);
        boolean beforeEnd = effectiveTo == null || instant.isBefore(effectiveTo);
        return afterStart && beforeEnd;
    }
}
