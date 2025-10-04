package com.patra.registry.domain.model.read.provenance;

import com.patra.registry.domain.exception.DomainValidationException;

import java.time.Instant;

/**
 * 时间窗口与增量指针配置查询视图。
 */
public record WindowOffsetQuery(
        Long id,
        Long provenanceId,
        String taskType,
        String taskTypeKey,
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
        Integer maxWindowSpanSeconds
) {
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
        taskType = taskType != null ? taskType.trim() : null;
        taskTypeKey = taskTypeKey != null ? taskTypeKey.trim() : "ALL";
        windowModeCode = windowModeCode.trim();
        windowSizeUnitCode = windowSizeUnitCode.trim();
        calendarAlignTo = calendarAlignTo != null ? calendarAlignTo.trim() : null;
        lookbackUnitCode = lookbackUnitCode != null ? lookbackUnitCode.trim() : null;
        overlapUnitCode = overlapUnitCode != null ? overlapUnitCode.trim() : null;
        offsetTypeCode = offsetTypeCode.trim();
        offsetFieldName = offsetFieldName != null ? offsetFieldName.trim() : null;
        offsetDateFormat = offsetDateFormat != null ? offsetDateFormat.trim() : null;
        defaultDateFieldName = defaultDateFieldName != null ? defaultDateFieldName.trim() : null;
    }
}
