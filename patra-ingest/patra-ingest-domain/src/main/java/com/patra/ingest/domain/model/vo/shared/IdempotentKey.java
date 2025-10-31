package com.patra.ingest.domain.model.vo.shared;

/**
 * Value object representing a 64-character SHA-256 idempotency key.
 *
 * <p>Used to deduplicate plans, tasks, and other domain objects; length is validated at creation.
 */
public record IdempotentKey(String value) {
  public IdempotentKey {
    if (value == null || value.length() != 64) {
      throw new IllegalArgumentException("IdempotentKey must be 64-char SHA256 hex");
    }
  }
}
