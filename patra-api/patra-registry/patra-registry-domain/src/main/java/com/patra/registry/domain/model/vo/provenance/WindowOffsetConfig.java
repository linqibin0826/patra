package com.patra.registry.domain.model.vo.provenance;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;

/**
 * Domain value object for {@code reg_prov_window_offset_cfg}.
 *
 * <p>Configures windowing and incremental offset tracking (DATE/ID/COMPOSITE).</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record WindowOffsetConfig(
        /* Primary key; unique window offset configuration identifier */
        Long id,
        /* Foreign key referencing {@code reg_provenance.id} */
        Long provenanceId,
        /* Operation type discriminator (HARVEST/UPDATE/BACKFILL); {@code null} applies to all */
        String operationType,
        /* Normalized operation type key; defaults to {@code ALL} when {@code operationType} is {@code null} */
        String operationTypeKey,
        /* Inclusive timestamp marking when this window offset configuration becomes effective */
        Instant effectiveFrom,
        /* Exclusive timestamp marking when this window offset configuration expires; {@code null} means open-ended */
        Instant effectiveTo,
        /* Window mode code (DICT CODE: window_mode); defines windowing strategy (SLIDING/TUMBLING/HOPPING) */
        String windowModeCode,
        /* Size/length of each time window; must be positive */
        Integer windowSizeValue,
        /* Unit of {@code windowSizeValue} (DICT CODE: time_unit); e.g., SECOND/MINUTE/HOUR/DAY */
        String windowSizeUnitCode,
        /* Calendar alignment anchor (e.g., START_OF_DAY/START_OF_HOUR); {@code null} means no alignment */
        String calendarAlignTo,
        /* Lookback value for historical data; defines how far back to start from current time */
        Integer lookbackValue,
        /* Unit of {@code lookbackValue} (DICT CODE: time_unit); e.g., SECOND/MINUTE/HOUR/DAY */
        String lookbackUnitCode,
        /* Overlap value between consecutive windows; prevents data gaps in boundary conditions */
        Integer overlapValue,
        /* Unit of {@code overlapValue} (DICT CODE: time_unit); e.g., SECOND/MINUTE/HOUR */
        String overlapUnitCode,
        /* Watermark lag in seconds; delay before considering a window complete to handle late-arriving data */
        Integer watermarkLagSeconds,
        /* Offset type code (DICT CODE: offset_type); defines tracking mechanism (DATE/ID/COMPOSITE) */
        String offsetTypeCode,
        /* Field name used for offset tracking (e.g., updated_at, pmid); must match API response field */
        String offsetFieldName,
        /* Date format pattern for offset parsing (e.g., yyyy-MM-dd'T'HH:mm:ss); only applies to DATE offset type */
        String offsetDateFormat,
        /* Default date field name when offset field is missing; fallback for offset extraction */
        String defaultDateFieldName,
        /* Maximum IDs to track per window; prevents unbounded state growth, {@code null} means no limit */
        Integer maxIdsPerWindow,
        /* Maximum window span in seconds; safety cap to prevent excessive time range queries */
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

        this.id = id; // already validated
        this.provenanceId = provenanceId; // already validated
        this.operationType = operationType != null ? operationType.trim() : null;
        this.operationTypeKey = operationTypeKey != null ? operationTypeKey.trim() : "ALL";
        this.effectiveFrom = effectiveFrom; // already validated as non-null
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
