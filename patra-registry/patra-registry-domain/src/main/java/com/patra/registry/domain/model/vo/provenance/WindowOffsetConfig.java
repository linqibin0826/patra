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
    /* Offset field name or JSONPath (DATE/ID field or composite key primary dimension) */
    String offsetFieldName,
    /* DATE offset format/semantics (e.g., ISO_INSTANT/epochMillis/YYYYMMDD) */
    String offsetDateFormat,
    /* Default incremental date field (e.g., PubMed: EDAT/PDAT/MHDA; Crossref: indexed-date) */
    String defaultDateFieldName,
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
   * @param offsetFieldName offset field name or JSONPath, nullable
   * @param offsetDateFormat date offset format, nullable
   * @param defaultDateFieldName default date field name, nullable
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
      String offsetFieldName,
      String offsetDateFormat,
      String defaultDateFieldName,
      Integer maxIdsPerWindow,
      Integer maxWindowSpanSeconds) {
    DomainValidationException.positive(id, "Window offset config id");
    DomainValidationException.positive(provenanceId, "Provenance id");
    String modeTrimmed = DomainValidationException.notBlank(windowModeCode, "Window mode code");
    String sizeUnitTrimmed =
        DomainValidationException.notBlank(windowSizeUnitCode, "Window size unit code");
    String offsetTypeTrimmed =
        DomainValidationException.notBlank(offsetTypeCode, "Offset type code");
    DomainValidationException.nonNull(effectiveFrom, "Effective from");

    this.id = id;
    this.provenanceId = provenanceId;
    this.operationType = operationType != null ? operationType.trim() : null;
    this.effectiveFrom = effectiveFrom;
    this.effectiveTo = effectiveTo;
    this.windowModeCode = modeTrimmed;
    this.windowSizeValue = windowSizeValue;
    this.windowSizeUnitCode = sizeUnitTrimmed;
    this.calendarAlignTo = calendarAlignTo != null ? calendarAlignTo.trim() : null;
    this.lookbackValue = lookbackValue;
    this.lookbackUnitCode = lookbackUnitCode != null ? lookbackUnitCode.trim() : null;
    this.overlapValue = overlapValue;
    this.overlapUnitCode = overlapUnitCode != null ? overlapUnitCode.trim() : null;
    this.watermarkLagSeconds = watermarkLagSeconds;
    this.offsetTypeCode = offsetTypeTrimmed;
    this.offsetFieldName = offsetFieldName != null ? offsetFieldName.trim() : null;
    this.offsetDateFormat = offsetDateFormat != null ? offsetDateFormat.trim() : null;
    this.defaultDateFieldName = defaultDateFieldName != null ? defaultDateFieldName.trim() : null;
    this.maxIdsPerWindow = maxIdsPerWindow;
    this.maxWindowSpanSeconds = maxWindowSpanSeconds;
  }
}
