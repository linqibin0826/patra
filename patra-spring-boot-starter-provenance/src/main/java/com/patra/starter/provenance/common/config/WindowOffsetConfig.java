package com.patra.starter.provenance.common.config;

/**
 * Window offset configuration.
 *
 * <p>Field descriptions:
 *
 * @param windowModeCode sliding window selection strategy identifier
 * @param windowSizeValue window size magnitude used for incremental runs
 * @param windowSizeUnitCode unit for the window size (e.g. DAY, WEEK)
 * @param lookbackValue number of units to look back when scheduling a window
 * @param lookbackUnitCode unit for the lookback value
 * @param overlapValue overlap between consecutive windows to avoid gaps
 * @param overlapUnitCode unit for the overlap amount
 * @param offsetTypeCode offset type hint consumed by scheduler logic
 * @param maxIdsPerWindow cap on identifiers processed per window
 * @author linqibin
 * @since 0.1.0
 */
public record WindowOffsetConfig(
    String windowModeCode,
    Integer windowSizeValue,
    String windowSizeUnitCode,
    Integer lookbackValue,
    String lookbackUnitCode,
    Integer overlapValue,
    String overlapUnitCode,
    String offsetTypeCode,
    Integer maxIdsPerWindow) {}
