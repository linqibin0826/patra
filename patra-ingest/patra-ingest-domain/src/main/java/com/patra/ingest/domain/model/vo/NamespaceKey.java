package com.patra.ingest.domain.model.vo;

import com.patra.ingest.domain.model.enums.NamespaceScope;

/**
 * Composite namespace key comprised of a scope and value.
 *
 * <p>The GLOBAL scope conventionally uses 64 {@code '0'} characters as the placeholder.
 */
public record NamespaceKey(NamespaceScope scope, String key) {
  /** Factory for the global namespace key (64 zeros). */
  public static NamespaceKey global() {
    return new NamespaceKey(NamespaceScope.GLOBAL, "0".repeat(64));
  }
}
