package com.patra.ingest.domain.model.vo;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Normalized cursor watermark.
 * <p>A cursor may be represented as time, numeric, or raw string values; this record carries the normalized forms.</p>
 * <ul>
 *   <li>{@code observedMaxValue}: raw maximum cursor value observed</li>
 *   <li>{@code normalizedInstant}: parsed instant when available</li>
 *   <li>{@code normalizedNumeric}: parsed numeric representation when available</li>
 * </ul>
 * Fields may be {@code null} when that representation is not available.
 */
public record CursorWatermark(String observedMaxValue, Instant normalizedInstant, BigDecimal normalizedNumeric) {

    /**
     * Watermark placeholder for initial runs (no observation yet).
     */
    public static CursorWatermark empty() {
        return new CursorWatermark(null, null, null);
    }

    /**
     * Indicates whether a normalized instant watermark is present.
     */
    public boolean hasInstant() {
        return normalizedInstant != null;
    }

    /**
     * Indicates whether a normalized numeric watermark is present.
     */
    public boolean hasNumeric() {
        return normalizedNumeric != null;
    }
}
