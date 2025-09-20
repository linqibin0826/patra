package com.patra.registry.contract.query.view.provenance;

import java.time.Instant;

/**
 * 时间窗口与增量指针配置查询视图。
 */
public record WindowOffsetQuery(
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
    public WindowOffsetQuery {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Window offset id must be positive");
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
        scopeCode = scopeCode.trim();
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
