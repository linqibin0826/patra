package com.patra.starter.objectstorage;

import java.util.Collections;
import java.util.Map;

/**
 * Resolved storage location containing bucket/object key pair and the business metadata that must
 * be persisted alongside file records.
 */
public record StorageLocation(
    String bucket,
    String objectKey,
    String businessId,
    String storageKey,
    Map<String, Object> correlationData) {

  public StorageLocation(String bucket, String objectKey, String businessId) {
    this(bucket, objectKey, businessId, bucket + "/" + objectKey, Collections.emptyMap());
  }

  public StorageLocation(
      String bucket, String objectKey, String businessId, Map<String, Object> correlationData) {
    this(bucket, objectKey, businessId, bucket + "/" + objectKey, correlationData);
  }

  public StorageLocation {
    if (!hasText(bucket)) {
      throw new IllegalArgumentException("bucket cannot be blank");
    }
    if (!hasText(objectKey)) {
      throw new IllegalArgumentException("objectKey cannot be blank");
    }
    if (!hasText(businessId)) {
      throw new IllegalArgumentException("businessId cannot be blank");
    }
    storageKey = hasText(storageKey) ? storageKey : bucket + "/" + objectKey;
    correlationData =
        correlationData == null
            ? Collections.emptyMap()
            : Collections.unmodifiableMap(correlationData);
  }

  private static boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }
}
