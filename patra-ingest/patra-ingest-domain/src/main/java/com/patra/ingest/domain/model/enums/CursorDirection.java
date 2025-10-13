package com.patra.ingest.domain.model.enums;

import lombok.Getter;

/**
 * Cursor advancement direction (DICT: ing_cursor_direction).
 *
 * <p>Field mapping: {@code ing_cursor_event.direction_code → FORWARD/BACKFILL}.
 */
@Getter
public enum CursorDirection {
  FORWARD("FORWARD", "Forward"),
  BACKFILL("BACKFILL", "Backfill");

  private final String code;
  private final String description;

  CursorDirection(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static CursorDirection fromCode(String value) {
    if (value == null) {
      throw new IllegalArgumentException("Cursor direction code cannot be null");
    }
    String n = value.trim().toUpperCase();
    for (CursorDirection e : values()) {
      if (e.code.equals(n)) return e;
    }
    throw new IllegalArgumentException("Unknown cursor direction code: " + value);
  }
}
