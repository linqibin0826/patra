package com.patra.registry.domain.model.vo.provenance;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;

/**
 * Domain value object for {@code reg_prov_window_offset_cfg}.
 *
 * <p>Configures how tasks segment time windows and advance incremental offsets (DATE/ID/COMPOSITE),
 * supporting lookback/overlap/watermark lag strategies.
 *
 * @author linqibin
 * @since 0.1.0
 */
public record WindowOffsetConfig(
    /* Primary key; unique window offset configuration identifier */
    Long id,
    /* Foreign key referencing reg_provenance.id */
    Long provenanceId,
    /* Operation type discriminator (HARVEST/UPDATE/BACKFILL); null applies to all */
    String operationType,
    /* Inclusive timestamp marking when this window offset configuration becomes effective */
    Instant effectiveFrom,
    /* Exclusive timestamp marking when this window offset configuration expires; null means open-ended */
    Instant effectiveTo,
    /* Window mode code (DICT CODE: window_mode); defines windowing strategy (SLIDING/CALENDAR) */
    String windowModeCode,
    /* Numeric part of window length; must be positive */
    Integer windowSizeValue,
    /* Unit of windowSizeValue (DICT CODE: time_unit); e.g., SECOND/MINUTE/HOUR/DAY */
    String windowSizeUnitCode,
    /* Alignment granularity for CALENDAR mode (e.g., HOUR/DAY/WEEK/MONTH); null for SLIDING mode */
    String calendarAlignTo,
    /* Lookback length value to compensate for late data */
    Integer lookbackValue,
    /* Unit for lookback length (DICT CODE: time_unit) */
    String lookbackUnitCode,
    /* Overlap length value between adjacent windows */
    Integer overlapValue,
    /* Unit for window overlap (DICT CODE: time_unit) */
    String overlapUnitCode,
    /* Watermark lag in seconds; max allowed lateness for out-of-order data */
    Integer watermarkLagSeconds,
    /* Offset type code (DICT CODE: offset_type); defines tracking mechanism (DATE/ID/COMPOSITE) */
    String offsetTypeCode,
    /* Unified field key (std_key) used as the offset pivot */
    String offsetFieldKey,
    /* DATE offset format/semantics (e.g., ISO_INSTANT/epochMillis/YYYYMMDD) */
    String offsetDateFormat,
    /* Unified date field key (std_key) used for time slicing when DATE/COMPOSITE */
    String windowDateFieldKey,
    /* Maximum IDs per window; split window when exceeded */
    Integer maxIdsPerWindow,
    /* Maximum span per window in seconds; overly long windows will be split */
    Integer maxWindowSpanSeconds) {
  /**
   * Canonical constructor with validation.
   *
   * @param id unique configuration identifier, must be positive
   * @param provenanceId provenance identifier, must be positive
   * @param operationType operation type discriminator, nullable
   * @param effectiveFrom effective start timestamp, must not be null
   * @param effectiveTo effective end timestamp, nullable (open-ended)
   * @param windowModeCode window mode code from dictionary, must not be blank
   * @param windowSizeValue window size numeric value, nullable
   * @param windowSizeUnitCode window size unit code from dictionary, must not be blank
   * @param calendarAlignTo calendar alignment anchor, nullable
   * @param lookbackValue lookback length value, nullable
   * @param lookbackUnitCode lookback unit code from dictionary, nullable
   * @param overlapValue overlap length value, nullable
   * @param overlapUnitCode overlap unit code from dictionary, nullable
   * @param watermarkLagSeconds watermark lag in seconds, nullable
   * @param offsetTypeCode offset type code from dictionary, must not be blank
   * @param offsetFieldKey unified offset field key, nullable
   * @param offsetDateFormat date offset format, nullable
   * @param windowDateFieldKey unified window date field key, nullable
   * @param maxIdsPerWindow maximum IDs per window, nullable
   * @param maxWindowSpanSeconds maximum window span in seconds, nullable
   * @throws DomainValidationException if validation fails
   */
  public WindowOffsetConfig(
      Long id,
      Long provenanceId,
      String operationType,
      Instant effectiveFrom,
      Instant effectiveTo,
      String windowModeCode,
      Integer windowSizeValue,
      String windowSizeUnitCode,
      String calendarAlignTo,
      Integer lookbackValue,
      String lookbackUnitCode,
      Integer overlapValue,
      String overlapUnitCode,
      Integer watermarkLagSeconds,
      String offsetTypeCode,
      String offsetFieldKey,
      String offsetDateFormat,
      String windowDateFieldKey,
      Integer maxIdsPerWindow,
      Integer maxWindowSpanSeconds) {
    validateRequiredFields(id, provenanceId, effectiveFrom);
    String modeTrimmed = DomainValidationException.notBlank(windowModeCode, "Window mode code");
    String sizeUnitTrimmed =
        DomainValidationException.notBlank(windowSizeUnitCode, "Window size unit code");
    String offsetTypeTrimmed =
        DomainValidationException.notBlank(offsetTypeCode, "Offset type code");

    this.id = id;
    this.provenanceId = provenanceId;
    this.operationType = trimOrNull(operationType);
    this.effectiveFrom = effectiveFrom;
    this.effectiveTo = effectiveTo;
    this.windowModeCode = modeTrimmed;
    this.windowSizeValue = windowSizeValue;
    this.windowSizeUnitCode = sizeUnitTrimmed;
    this.calendarAlignTo = trimOrNull(calendarAlignTo);
    this.lookbackValue = lookbackValue;
    this.lookbackUnitCode = trimOrNull(lookbackUnitCode);
    this.overlapValue = overlapValue;
    this.overlapUnitCode = trimOrNull(overlapUnitCode);
    this.watermarkLagSeconds = watermarkLagSeconds;

    String offsetFieldKeyNormalized = normalizeToNullIfEmpty(offsetFieldKey);
    String windowDateFieldKeyNormalized = normalizeToNullIfEmpty(windowDateFieldKey);
    validateDateOffsetKeys(
        offsetTypeTrimmed, offsetFieldKeyNormalized, windowDateFieldKeyNormalized);

    this.offsetTypeCode = offsetTypeTrimmed;
    this.offsetFieldKey = offsetFieldKeyNormalized;
    this.offsetDateFormat = trimOrNull(offsetDateFormat);
    this.windowDateFieldKey = windowDateFieldKeyNormalized;
    this.maxIdsPerWindow = maxIdsPerWindow;
    this.maxWindowSpanSeconds = maxWindowSpanSeconds;
  }

  /**
   * Validates required fields for the configuration.
   *
   * @param id configuration identifier
   * @param provenanceId provenance identifier
   * @param effectiveFrom effective start timestamp
   */
  private static void validateRequiredFields(Long id, Long provenanceId, Instant effectiveFrom) {
    DomainValidationException.positive(id, "Window offset config id");
    DomainValidationException.positive(provenanceId, "Provenance id");
    DomainValidationException.nonNull(effectiveFrom, "Effective from");
  }

  /**
   * Validates that DATE/COMPOSITE offset types have required field keys.
   *
   * @param offsetTypeCode offset type code
   * @param offsetFieldKey normalized offset field key
   * @param windowDateFieldKey normalized window date field key
   */
  private static void validateDateOffsetKeys(
      String offsetTypeCode, String offsetFieldKey, String windowDateFieldKey) {
    boolean requiresDateKey =
        "DATE".equalsIgnoreCase(offsetTypeCode) || "COMPOSITE".equalsIgnoreCase(offsetTypeCode);
    if (requiresDateKey && offsetFieldKey == null && windowDateFieldKey == null) {
      throw new DomainValidationException(
          "DATE/COMPOSITE offset requires at least one std_key (offset or window date)");
    }
  }

  /**
   * Trims string and returns null if the input is null.
   *
   * @param value string to trim
   * @return trimmed string or null
   */
  private static String trimOrNull(String value) {
    return value != null ? value.trim() : null;
  }

  /**
   * Normalizes string to null if empty after trimming.
   *
   * @param value string to normalize
   * @return trimmed string or null if empty
   */
  private static String normalizeToNullIfEmpty(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
