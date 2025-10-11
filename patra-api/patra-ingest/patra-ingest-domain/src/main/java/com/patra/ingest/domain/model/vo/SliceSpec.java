package com.patra.ingest.domain.model.vo;

import java.time.Instant;
import java.util.Map;

/**
 * Slice boundary specification value object.
 * <p>Abstracts different slicing dimensions such as time windows, ID ranges, token segments, and additional parameters.</p>
 * <ul>
 *   <li>{@code windowFrom}/{@code windowTo}: half-open time boundaries</li>
 *   <li>{@code idRangeFrom}/{@code idRangeTo}: identifier range (semantics interpreted by the strategy)</li>
 *   <li>{@code extra}: read-only extension map</li>
 * </ul>
 * Invariant: {@code extra} is never {@code null} and is defensively copied.
 */
public record SliceSpec(
        Instant windowFrom,
        Instant windowTo,
        String idRangeFrom,
        String idRangeTo,
        Map<String, Object> extra
) {
    public SliceSpec {
        extra = extra == null ? Map.of() : Map.copyOf(extra);
    }
}
