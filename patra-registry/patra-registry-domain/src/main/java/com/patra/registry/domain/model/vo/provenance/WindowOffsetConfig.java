package com.patra.registry.domain.model.vo.provenance;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;

/**
 * {@code reg_prov_window_offset_cfg} 的领域值对象。
 */
public record WindowOffsetConfig(
        Long id,
        Long provenanceId,
        String operationType,
        String operationTypeKey,
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
                              String operationType,
                              String operationTypeKey,
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
        String sizeUnitTrimmed = DomainValidationException.notBlank(windowSizeUnitCode, "Window size unit code");
        String offsetTypeTrimmed = DomainValidationException.notBlank(offsetTypeCode, "Offset type code");
        DomainValidationException.nonNull(effectiveFrom, "Effective from");

        this.id = id; // 已验证
        this.provenanceId = provenanceId; // 已验证
        this.operationType = operationType != null ? operationType.trim() : null;
        this.operationTypeKey = operationTypeKey != null ? operationTypeKey.trim() : "ALL";
        this.effectiveFrom = effectiveFrom; // 非 null 已验证
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
