package com.patra.ingest.domain.model.enums;

import lombok.Getter;

/**
 * Cursor type (DICT: ing_cursor_type).
 *
 * <p>Field mapping: {@code cursor_type_code → TIME/ID/TOKEN}.
 */
@Getter
public enum CursorType {
  TIME("TIME", "Time-based"),
  ID("ID", "Identifier-based"),
  TOKEN("TOKEN", "Token-based");

  private final String code;
  private final String description;

  CursorType(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static CursorType fromCode(String value) {
    if (value == null) {
      throw new IllegalArgumentException("Cursor type code cannot be null");
    }
    String n = value.trim().toUpperCase();
    for (CursorType e : values()) {
      if (e.code.equals(n)) return e;
    }
    throw new IllegalArgumentException("Unknown cursor type code: " + value);
  }
}
