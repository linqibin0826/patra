package com.patra.ingest.domain.model.enums;

import lombok.Getter;

/**
 * Namespace scope (DICT: ing_namespace_scope).
 *
 * <p>Distinguishes cursor namespaces: GLOBAL/EXPR/CUSTOM; mapped to {@code namespace_scope_code}.
 */
@Getter
public enum NamespaceScope {
  GLOBAL("GLOBAL", "Global namespace"),
  EXPR("EXPR", "Expression-hash namespace"),
  CUSTOM("CUSTOM", "Custom namespace");

  private final String code;
  private final String description;

  NamespaceScope(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static NamespaceScope fromCode(String value) {
    if (value == null) {
      throw new IllegalArgumentException("Namespace scope code cannot be null");
    }
    String n = value.trim().toUpperCase();
    for (NamespaceScope e : values()) {
      if (e.code.equals(n)) return e;
    }
    throw new IllegalArgumentException("Unknown namespace scope code: " + value);
  }
}
