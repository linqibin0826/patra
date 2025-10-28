package com.patra.common.storage;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable context containing all information required to generate an object storage key.
 *
 * <p>This record encapsulates the standard segments used in object key generation across all
 * Papertrace microservices, ensuring consistent naming conventions and structure.
 *
 * <p><b>Standard Object Key Pattern</b>: {@code
 * {service}/{business-type}/{yyyy}/{MM}/{dd}/{business-id}.{extension}}
 *
 * <p><b>Example</b>: {@code ingest/literature-batch/2025/10/26/pubmed-123-batch-001.json}
 *
 * @param serviceName microservice name (short form, e.g., "ingest", "storage", "catalog")
 * @param businessType business category in kebab-case (e.g., "literature-batch",
 *     "metadata-snapshot")
 * @param businessId unique identifier for this specific business entity (e.g.,
 *     "pubmed-123-batch-001")
 * @param partitionDate date used for time-based partitioning (yyyy/MM/dd structure)
 * @param extension file extension without leading dot (e.g., "json", "json.gz", "xml")
 * @param customSegments optional key-value pairs for custom path segments (immutable map)
 * @author linqibin
 * @since 0.1.0
 */
public record ObjectKeyContext(
    String serviceName,
    String businessType,
    String businessId,
    LocalDate partitionDate,
    String extension,
    Map<String, String> customSegments) {

  /**
   * Compact constructor with validation and defensive copying.
   *
   * @throws IllegalArgumentException if any required field is blank or null
   */
  public ObjectKeyContext {
    validateNonBlank(serviceName, "serviceName");
    validateNonBlank(businessType, "businessType");
    validateNonBlank(businessId, "businessId");
    Objects.requireNonNull(partitionDate, "partitionDate cannot be null");
    validateNonBlank(extension, "extension");

    // Ensure custom segments is immutable and non-null
    customSegments = sanitizeCustomSegments(customSegments);
  }

  /**
   * Creates a context with only the required fields (no custom segments).
   *
   * @param serviceName microservice name (short form)
   * @param businessType business category (kebab-case)
   * @param businessId unique business identifier
   * @param partitionDate date for partitioning
   * @param extension file extension
   * @return initialized context
   */
  public static ObjectKeyContext of(
      String serviceName,
      String businessType,
      String businessId,
      LocalDate partitionDate,
      String extension) {
    return new ObjectKeyContext(
        serviceName, businessType, businessId, partitionDate, extension, Map.of());
  }

  /**
   * Creates a builder for fluent construction with optional custom segments.
   *
   * @return new builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /** Validates that a string is neither null nor blank. */
  private static void validateNonBlank(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " cannot be null or blank");
    }
  }

  /** Ensures custom segments map is immutable and non-null. */
  private static Map<String, String> sanitizeCustomSegments(Map<String, String> source) {
    if (source == null || source.isEmpty()) {
      return Map.of();
    }
    Map<String, String> copy = new LinkedHashMap<>(source.size());
    source.forEach(
        (key, value) -> {
          Objects.requireNonNull(key, "Custom segment key cannot be null");
          copy.put(key, value);
        });
    return Collections.unmodifiableMap(copy);
  }

  /** Builder for {@link ObjectKeyContext} with fluent API. */
  public static final class Builder {
    private String serviceName;
    private String businessType;
    private String businessId;
    private LocalDate partitionDate;
    private String extension;
    private Map<String, String> customSegments = new LinkedHashMap<>();

    private Builder() {}

    public Builder serviceName(String serviceName) {
      this.serviceName = serviceName;
      return this;
    }

    public Builder businessType(String businessType) {
      this.businessType = businessType;
      return this;
    }

    public Builder businessId(String businessId) {
      this.businessId = businessId;
      return this;
    }

    public Builder partitionDate(LocalDate partitionDate) {
      this.partitionDate = partitionDate;
      return this;
    }

    public Builder extension(String extension) {
      this.extension = extension;
      return this;
    }

    /**
     * Adds a custom segment key-value pair.
     *
     * @param key segment name
     * @param value segment value
     * @return this builder
     */
    public Builder customSegment(String key, String value) {
      this.customSegments.put(key, value);
      return this;
    }

    /**
     * Builds the immutable context instance.
     *
     * @return initialized {@link ObjectKeyContext}
     * @throws IllegalArgumentException if any required field is missing or invalid
     */
    public ObjectKeyContext build() {
      return new ObjectKeyContext(
          serviceName, businessType, businessId, partitionDate, extension, customSegments);
    }
  }
}
