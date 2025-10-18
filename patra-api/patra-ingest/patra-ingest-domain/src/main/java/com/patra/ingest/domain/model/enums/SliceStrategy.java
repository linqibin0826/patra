package com.patra.ingest.domain.model.enums;

import java.util.Optional;

/**
 * Slice strategy enumeration that defines all supported window types.
 *
 * <p>This enum is used for both plan-level window specifications and slice-level strategies.
 *
 * <p>Strategy types:
 *
 * <ul>
 *   <li>TIME: Time-based windowing with timestamp precision (e.g., 2024-01-01T00:00:00Z to
 *       2024-12-31T23:59:59Z)
 *   <li>DATE: Date-only windowing without time component (e.g., 2024-01-01 to 2024-12-31)
 *   <li>ID_RANGE: ID range windowing (e.g., ID 1000000 to 2000000)
 *   <li>CURSOR_LANDMARK: Cursor/token-based windowing (e.g., pagination tokens)
 *   <li>VOLUME_BUDGET: Volume-based windowing (e.g., fetch up to 100k records)
 *   <li>HYBRID: Combined strategy (e.g., time + ID + volume constraints)
 *   <li>SINGLE: Single slice (no partitioning, typically for UPDATE operations)
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
public enum SliceStrategy {

  /** Time-based windowing strategy (includes timestamp with time component). */
  TIME("TIME"),

  /** Date-only windowing strategy (day-level granularity, no time component). */
  DATE("DATE"),

  /** ID range windowing strategy. */
  ID_RANGE("ID_RANGE"),

  /** Cursor/token-based windowing strategy. */
  CURSOR_LANDMARK("CURSOR_LANDMARK"),

  /** Volume budget windowing strategy. */
  VOLUME_BUDGET("VOLUME_BUDGET"),

  /** Hybrid strategy combining multiple constraints. */
  HYBRID("HYBRID"),

  /** Single slice strategy (no partitioning). */
  SINGLE("SINGLE");

  private final String code;

  SliceStrategy(String code) {
    this.code = code;
  }

  /** Returns the strategy code for persistence and JSON serialization. */
  public String getCode() {
    return code;
  }

  /**
   * Parses strategy enum from code string.
   *
   * @param code strategy code
   * @return matching strategy enum wrapped in Optional
   */
  public static Optional<SliceStrategy> fromCode(String code) {
    if (code == null || code.isBlank()) {
      return Optional.empty();
    }
    for (SliceStrategy strategy : values()) {
      if (strategy.code.equalsIgnoreCase(code)) {
        return Optional.of(strategy);
      }
    }
    return Optional.empty();
  }
}
