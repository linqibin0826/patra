package com.patra.expr;

/**
 * Represents whether a text-based comparison is case-sensitive.
 *
 * <p>Used in TERM and IN operations to control case sensitivity behavior.
 */
public enum CaseSensitivity {
  /** Case-insensitive comparison where "Text" matches "text". */
  INSENSITIVE,

  /** Case-sensitive comparison where "Text" does not match "text". */
  SENSITIVE;

  /**
   * Converts a boolean flag to a CaseSensitivity value.
   *
   * @param caseSensitive true for SENSITIVE, false for INSENSITIVE
   * @return corresponding CaseSensitivity value
   */
  public static CaseSensitivity of(boolean caseSensitive) {
    return caseSensitive ? SENSITIVE : INSENSITIVE;
  }

  /**
   * Checks if this instance represents case-sensitive matching.
   *
   * @return true if SENSITIVE, false if INSENSITIVE
   */
  public boolean isSensitive() {
    return this == SENSITIVE;
  }
}
