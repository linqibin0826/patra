package com.patra.ingest.domain.model.vo.cursor;

import com.patra.ingest.domain.model.enums.CursorType;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Normalized cursor value (time, numeric, or token).
 *
 * <p>Retains the raw string alongside parsed forms for comparison and sorting.
 *
 * <ul>
 *   <li>{@code type}: cursor type {@link CursorType}
 *   <li>{@code raw}: normalized raw value
 *   <li>{@code instant}: parsed instant when the cursor represents time
 *   <li>{@code numeric}: parsed numeric value when applicable
 * </ul>
 */
public record CursorValue(CursorType type, String raw, Instant instant, BigDecimal numeric) {
  /** Build a time-based cursor value. */
  public static CursorValue time(Instant v) {
    return new CursorValue(CursorType.TIME, v.toString(), v, null);
  }

  /** Build a numeric (ID) cursor value. */
  public static CursorValue id(BigDecimal v) {
    return new CursorValue(CursorType.ID, v.toPlainString(), null, v);
  }

  /** Build a string token cursor value. */
  public static CursorValue token(String token) {
    return new CursorValue(CursorType.TOKEN, token, null, null);
  }

  /** Build an empty/default cursor value. */
  public static CursorValue empty() {
    return new CursorValue(CursorType.TIME, null, null, null);
  }
}
