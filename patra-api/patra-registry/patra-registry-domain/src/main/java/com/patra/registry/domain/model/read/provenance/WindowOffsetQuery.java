package com.patra.registry.domain.model.read.provenance;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;

/**
 * Window offset configuration query view.
 *
 * <p>Read-optimized projection for querying time window and offset configuration.
 *
 * @author linqibin
 * @since 0.1.0
 */
public record WindowOffsetQuery(
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
  public WindowOffsetQuery {
    if (id == null || id <= 0) {
      throw new DomainValidationException("Window offset id must be positive");
    }
    if (provenanceId == null || provenanceId <= 0) {
      throw new DomainValidationException("Provenance id must be positive");
    }
    if (windowModeCode == null || windowModeCode.isBlank()) {
      throw new DomainValidationException("Window mode code cannot be blank");
    }
    if (windowSizeUnitCode == null || windowSizeUnitCode.isBlank()) {
      throw new DomainValidationException("Window size unit code cannot be blank");
    }
    if (offsetTypeCode == null || offsetTypeCode.isBlank()) {
      throw new DomainValidationException("Offset type code cannot be blank");
    }
    if (effectiveFrom == null) {
      throw new DomainValidationException("Effective from cannot be null");
    }
    operationType = operationType != null ? operationType.trim() : null;
    windowModeCode = windowModeCode.trim();
    windowSizeUnitCode = windowSizeUnitCode.trim();
    calendarAlignTo = calendarAlignTo != null ? calendarAlignTo.trim() : null;
    lookbackUnitCode = lookbackUnitCode != null ? lookbackUnitCode.trim() : null;
    overlapUnitCode = overlapUnitCode != null ? overlapUnitCode.trim() : null;
    offsetTypeCode = offsetTypeCode.trim();
    offsetFieldKey = offsetFieldKey != null ? offsetFieldKey.trim() : null;
    if (offsetFieldKey != null && offsetFieldKey.isEmpty()) {
      offsetFieldKey = null;
    }
    offsetDateFormat = offsetDateFormat != null ? offsetDateFormat.trim() : null;
    windowDateFieldKey = windowDateFieldKey != null ? windowDateFieldKey.trim() : null;
    if (windowDateFieldKey != null && windowDateFieldKey.isEmpty()) {
      windowDateFieldKey = null;
    }
  }
}
