package com.patra.registry.domain.model.vo.provenance;

import java.time.Instant;

/**
 * {@code reg_prov_window_offset_cfg} 的领域值对象。
 */
public record WindowOffsetConfig(
        Long id,
        Long provenanceId,
        String scopeCode,
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
    public WindowOffsetConfig(Long id,
                              Long provenanceId,
                              String scopeCode,
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
                              Integer maxWindowSpanSeconds) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Window offset config id must be positive");
        }
        if (provenanceId == null || provenanceId <= 0) {
            throw new IllegalArgumentException("Provenance id must be positive");
        }
        if (scopeCode == null || scopeCode.isBlank()) {
            throw new IllegalArgumentException("Scope code cannot be blank");
        }
        if (windowModeCode == null || windowModeCode.isBlank()) {
            throw new IllegalArgumentException("Window mode code cannot be blank");
        }
        if (windowSizeUnitCode == null || windowSizeUnitCode.isBlank()) {
            throw new IllegalArgumentException("Window size unit code cannot be blank");
        }
        if (offsetTypeCode == null || offsetTypeCode.isBlank()) {
            throw new IllegalArgumentException("Offset type code cannot be blank");
        }
        if (effectiveFrom == null) {
            throw new IllegalArgumentException("Effective from cannot be null");
        }

        this.id = id;
        this.provenanceId = provenanceId;
        this.scopeCode = scopeCode.trim();
        this.taskType = taskType != null ? taskType.trim() : null;
        this.taskTypeKey = taskTypeKey != null ? taskTypeKey.trim() : "ALL";
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.windowModeCode = windowModeCode.trim();
        this.windowSizeValue = windowSizeValue;
        this.windowSizeUnitCode = windowSizeUnitCode.trim();
        this.calendarAlignTo = calendarAlignTo != null ? calendarAlignTo.trim() : null;
        this.lookbackValue = lookbackValue;
        this.lookbackUnitCode = lookbackUnitCode != null ? lookbackUnitCode.trim() : null;
        this.overlapValue = overlapValue;
        this.overlapUnitCode = overlapUnitCode != null ? overlapUnitCode.trim() : null;
        this.watermarkLagSeconds = watermarkLagSeconds;
        this.offsetTypeCode = offsetTypeCode.trim();
        this.offsetFieldName = offsetFieldName != null ? offsetFieldName.trim() : null;
        this.offsetDateFormat = offsetDateFormat != null ? offsetDateFormat.trim() : null;
        this.defaultDateFieldName = defaultDateFieldName != null ? defaultDateFieldName.trim() : null;
        this.maxIdsPerWindow = maxIdsPerWindow;
        this.maxWindowSpanSeconds = maxWindowSpanSeconds;
    }
}
