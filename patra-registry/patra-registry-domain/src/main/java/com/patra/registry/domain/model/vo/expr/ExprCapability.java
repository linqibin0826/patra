package com.patra.registry.domain.model.vo.expr;

import com.patra.registry.domain.exception.DomainValidationException;
import com.patra.registry.domain.support.TemporalEntity;
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
    String tokenValuePattern)
    implements TemporalEntity {
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
    validateRequiredFields(id, provenanceId, fieldKey, rangeKindCode, effectiveFrom);

    this.id = id;
    this.provenanceId = provenanceId;
    this.operationType = DomainValidationException.trimOrNull(operationType);
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
    this.termPattern = DomainValidationException.trimOrNull(termPattern);
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
    this.tokenValuePattern = DomainValidationException.trimOrNull(tokenValuePattern);
  }

  /**
   * Validates required fields for capability configuration.
   *
   * @param id capability identifier
   * @param provenanceId provenance identifier
   * @param fieldKey field key
   * @param rangeKindCode range kind code
   * @param effectiveFrom effective start timestamp
   */
  private static void validateRequiredFields(
      Long id, Long provenanceId, String fieldKey, String rangeKindCode, Instant effectiveFrom) {
    DomainValidationException.positive(id, "Capability id");
    DomainValidationException.positive(provenanceId, "Provenance id");
    DomainValidationException.notBlank(fieldKey, "Field key");
    DomainValidationException.notBlank(rangeKindCode, "Range kind code");
    DomainValidationException.nonNull(effectiveFrom, "Effective from");
  }
}
