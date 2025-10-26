package com.patra.storage.domain.model.vo;

import java.util.Objects;

/** Immutable storage locator containing the bucket and object key segments. */
public record StorageKey(String bucket, String objectKey) {

  /**
   * Creates a new storage key and validates mandatory parts.
   *
   * @param bucket logical bucket name (non blank)
   * @param objectKey object key path within the bucket (non blank)
   */
  public StorageKey {
    if (bucket == null || bucket.isBlank()) {
      throw new IllegalArgumentException("Bucket cannot be blank");
    }
    if (objectKey == null || objectKey.isBlank()) {
      throw new IllegalArgumentException("Object key cannot be blank");
    }
  }

  /**
   * Builds the canonical bucket/objectKey string used for persistence and idempotency checks.
   *
   * @return combined bucket/objectKey form (e.g. `bucket/objectKey`)
   */
  public String fullKey() {
    return bucket + '/' + objectKey;
  }

  /**
   * Convenience helper for equality with another raw bucket/key pair.
   *
   * @param otherBucket bucket name to compare
   * @param otherKey object key to compare
   * @return {@code true} if both segments match this key
   */
  public boolean matches(String otherBucket, String otherKey) {
    return Objects.equals(bucket, otherBucket) && Objects.equals(objectKey, otherKey);
  }
}
