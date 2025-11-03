package com.patra.registry.domain.model.read.expr;

import com.patra.registry.domain.exception.DomainValidationException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 表达式能力查询视图。
 *
 * <p>用于查询字段操作能力和约束的读优化投影。包含字段支持的操作类型、数据范围限制、验证规则等元数据。
 *
 * @author linqibin
 * @since 0.1.0
 */
public record ExprCapabilityQuery(
    Long provenanceId,
    String operationType,
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
    Instant effectiveTo) {
  public ExprCapabilityQuery {
    DomainValidationException.positive(provenanceId, "Provenance id");
    fieldKey = DomainValidationException.notBlank(fieldKey, "Field key");
    rangeKindCode = DomainValidationException.notBlank(rangeKindCode, "Range kind code");
    DomainValidationException.nonNull(effectiveFrom, "Effective from");
    operationType = operationType != null ? operationType.trim() : null;
    termPattern = termPattern != null ? termPattern.trim() : null;
    tokenValuePattern = tokenValuePattern != null ? tokenValuePattern.trim() : null;
  }
}
