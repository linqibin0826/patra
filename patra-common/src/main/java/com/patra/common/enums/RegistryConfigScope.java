package com.patra.common.enums;

import java.util.Locale;

/**
 * Distribution scope for registry configurations.
 *
 * <p>Identifies the level at which a registry entry applies—covering expression fields, rendering
 * rules, API parameter mappings, and provenance-related settings such as endpoints, pagination,
 * HTTP, retry, or rate limiting:
 *
 * <ul>
 *   <li>{@link #SOURCE}: applies to every task within a given provenance.
 *   <li>{@link #TASK}: applies to a specific task type and may override provenance-level defaults.
 * </ul>
 *
 * <p>This enumeration makes the configuration scope explicit in code and avoids confusion with
 * other scope values (for example, authorization or analytics scopes). Persisted columns continue
 * to store {@code SOURCE}/{@code TASK}, so no historical data migration is required.
 *
 * @author linqibin
 * @since 0.1.0
 * @see #fromCode(String)
 */
public enum RegistryConfigScope {
  /** Applies at the provenance (source) level. */
  SOURCE,
  /** Applies at the task-type level. */
  TASK;

  /** Returns the canonical uppercase code used for persistence and lookup. */
  public String code() {
    return name();
  }

  /**
   * Parses a code by ignoring case and surrounding whitespace.
   *
   * @param code persisted code value
   * @return matching scope
   * @throws IllegalArgumentException if the input is blank or unknown
   */
  public static RegistryConfigScope fromCode(String code) {
    if (code == null || code.isBlank()) {
      throw new IllegalArgumentException("Scope code cannot be blank");
    }
    try {
      return RegistryConfigScope.valueOf(code.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Unknown scope code: " + code, ex);
    }
  }
}
