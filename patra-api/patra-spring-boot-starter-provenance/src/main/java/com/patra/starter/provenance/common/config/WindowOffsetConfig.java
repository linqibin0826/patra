package com.patra.starter.provenance.common.config;

/**
 * Window offset configuration
 *
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
    Integer maxIdsPerWindow
) {
}
