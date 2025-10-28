package com.patra.starter.objectstorage;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

/**
 * Immutable context provided by callers when resolving storage locations.
 *
 * <p>Acts as the single source of truth for the resolver: all path-related inputs (business type
 * and filename), the business identifier written to the metadata store, optional correlation data
 * for downstream analytics, and the partition date.
 */
@Getter
@Builder(toBuilder = true)
public final class StorageContext {

  private final String businessType;
  private final String filename;
  private final String businessId;

  @Singular("correlationEntry")
  private final Map<String, Object> correlationData;

  @Builder.Default private final LocalDate date = LocalDate.now();

  public LocalDate getDate() {
    return date == null ? LocalDate.now() : date;
  }

  public Map<String, Object> getCorrelationData() {
    return correlationData == null
        ? Collections.emptyMap()
        : Collections.unmodifiableMap(correlationData);
  }

  /** Validates mandatory fields and guards against path traversal input. */
  public void validate() {
    require("businessType", businessType);
    require("filename", filename);
    require("businessId", businessId);
    rejectPathSeparator("businessType", businessType);
    rejectPathSeparator("filename", filename);
  }

  private static void require(String field, String value) {
    if (!hasText(value)) {
      throw new IllegalArgumentException(field + " is required");
    }
  }

  private static void rejectPathSeparator(String field, String value) {
    if (value == null) {
      return;
    }
    if (value.contains("/") || value.contains("\\")) {
      throw new IllegalArgumentException(field + " cannot contain path separators");
    }
  }

  private static boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }
}
