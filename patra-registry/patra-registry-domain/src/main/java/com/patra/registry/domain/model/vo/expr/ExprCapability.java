package com.patra.registry.domain.model.vo.expr;

import com.patra.registry.domain.exception.DomainValidationException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Domain value object for {@code reg_prov_expr_capability}.
 *
 * <p>Declares allowed expression operators and constraints per field/key and scope.
 *
 * @author linqibin
 * @since 0.1.0
 */
public record ExprCapability(
    /* Primary key; unique capability configuration identifier */
    Long id,
    /* Foreign key referencing {@code reg_provenance.id} */
    Long provenanceId,
    /* Operation type discriminator (HARVEST/UPDATE/BACKFILL); {@code null} applies to all */
    String operationType,
    /* Unified internal field key (logical FK to {@code reg_expr_field_dict.field_key}) */
    String fieldKey,
    /* Inclusive timestamp marking when this capability becomes effective */
    Instant effectiveFrom,
    /* Exclusive timestamp marking when this capability expires; {@code null} means open-ended */
    Instant effectiveTo,
    /* JSON array of allowed operation codes (e.g., ["TERM","IN","RANGE","EXISTS","TOKEN"]) */
    String opsJson,
    /* JSON array of operations that allow NOT; {@code null} means same as {@code opsJson} */
    String negatableOpsJson,
    /* Whether NOT is globally allowed for this field */
    boolean supportsNot,
    /* JSON array of allowed TERM match strategies (e.g., ["PHRASE","EXACT","ANY"]) */
    String termMatchesJson,
    /* Whether TERM operation supports case-sensitive matching */
    boolean termCaseSensitiveAllowed,
    /* Whether TERM allows blank/empty string values */
    boolean termAllowBlank,
    /* Minimum length for TERM values; {@code 0} means no limit */
    int termMinLength,
    /* Maximum length for TERM values; {@code 0} means no limit */
    int termMaxLength,
    /* Optional regex pattern to constrain TERM value charset/format */
    String termPattern,
    /* Maximum number of elements for IN set; {@code 0} means no limit */
    int inMaxSize,
    /* Whether IN operation supports case-sensitive matching */
    boolean inCaseSensitiveAllowed,
    /* Range kind code (DICT CODE: reg_range_kind) indicating RANGE value type (NONE/DATE/DATETIME/NUMBER) */
    String rangeKindCode,
    /* Whether RANGE allows open start (-inf, x] */
    boolean rangeAllowOpenStart,
    /* Whether RANGE allows open end [x, +inf) */
    boolean rangeAllowOpenEnd,
    /* Whether RANGE allows closed interval at infinity (e.g., (-inf, x]) */
    boolean rangeAllowClosedAtInfinity,
    /* Minimum DATE bound (UTC); applicable when {@code rangeKindCode} is {@code DATE} */
    LocalDate dateMin,
    /* Maximum DATE bound (UTC); applicable when {@code rangeKindCode} is {@code DATE} */
    LocalDate dateMax,
    /* Minimum DATETIME bound (UTC, microsecond precision); applicable when {@code rangeKindCode} is {@code DATETIME} */
    Instant datetimeMin,
    /* Maximum DATETIME bound (UTC, microsecond precision); applicable when {@code rangeKindCode} is {@code DATETIME} */
    Instant datetimeMax,
    /* Minimum NUMBER bound (high precision); applicable when {@code rangeKindCode} is {@code NUMBER} */
    BigDecimal numberMin,
    /* Maximum NUMBER bound (high precision); applicable when {@code rangeKindCode} is {@code NUMBER} */
    BigDecimal numberMax,
    /* Whether EXISTS operator is supported for this field */
    boolean existsSupported,
    /* JSON array of allowed token kinds (e.g., ["owner","pmcid"]) */
    String tokenKindsJson,
    /* Optional regex constraint for token values */
    String tokenValuePattern) {
  public ExprCapability(
      Long id,
      Long provenanceId,
      String operationType,
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

    this.id = id; // already validated
    this.provenanceId = provenanceId; // already validated
    this.operationType = operationType != null ? operationType.trim() : null;
    this.fieldKey = fieldKeyTrimmed;
    this.effectiveFrom = effectiveFrom; // already validated as non-null
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

  /**
   * Checks whether the capability is effective at the given instant.
   *
   * @param instant the time point to check (must not be null)
   * @return {@code true} if the capability is effective at the given instant
   * @throws DomainValidationException if {@code instant} is null
   */
  public boolean isEffectiveAt(Instant instant) {
    DomainValidationException.nonNull(instant, "Instant");
    boolean afterStart = !instant.isBefore(effectiveFrom);
    boolean beforeEnd = effectiveTo == null || instant.isBefore(effectiveTo);
    return afterStart && beforeEnd;
  }
}
