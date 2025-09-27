package com.patra.ingest.domain.model.vo;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 游标归一化水位信息。
 */
public record CursorWatermark(String observedMaxValue, Instant normalizedInstant, BigDecimal normalizedNumeric) {

    public static CursorWatermark empty() {
        return new CursorWatermark(null, null, null);
    }

    public boolean hasInstant() {
        return normalizedInstant != null;
    }

    public boolean hasNumeric() {
        return normalizedNumeric != null;
    }
}
