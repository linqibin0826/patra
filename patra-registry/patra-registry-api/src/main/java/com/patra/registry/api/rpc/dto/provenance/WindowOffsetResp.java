package com.patra.registry.api.rpc.dto.provenance;

import java.time.Instant;

/**
 * Response DTO describing window and offset configuration for incremental harvesting.
 *
 * <p>Field descriptions:
 *
 * <ol>
 *   <li>id - primary identifier of the configuration row
 *   <li>provenanceId - provenance owning the configuration
 *   <li>operationType - operation discriminator (e.g., HARVEST/UPDATE)
 *   <li>effectiveFrom - timestamp from which the configuration becomes effective
 *   <li>effectiveTo - timestamp until which the configuration remains effective
 *   <li>windowModeCode - window generation mode (FIXED/SLIDING/NONE)
 *   <li>windowSizeValue - numeric component of window size
 *   <li>windowSizeUnitCode - temporal unit for window size
 *   <li>calendarAlignTo - calendar alignment anchor for window boundaries
 *   <li>lookbackValue - amount of historical lookback applied on startup or replay
 *   <li>lookbackUnitCode - temporal unit for lookback value
 *   <li>overlapValue - overlap applied between sequential windows
 *   <li>overlapUnitCode - temporal unit for overlap value
 *   <li>watermarkLagSeconds - allowed lag when computing watermark in seconds
 *   <li>offsetTypeCode - offset evaluation strategy discriminator
 *   <li>offsetFieldName - payload field containing incremental pointer
 *   <li>offsetDateFormat - optional date format for offset parsing
 *   <li>defaultDateFieldName - fallback date field name when primary is absent
 *   <li>maxIdsPerWindow - safety cap for IDs processed per window
 *   <li>maxWindowSpanSeconds - maximum allowed window span in seconds
 * </ol>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record WindowOffsetResp(
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
    Integer maxWindowSpanSeconds) {}
