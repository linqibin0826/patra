package com.patra.storage.domain.model.vo;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Describes the upstream business context associated with a stored payload. */
public record BusinessContext(
    String serviceName,
    String businessType,
    String businessId,
    Map<String, Object> correlationData) {

  /**
   * Creates a new context ensuring key identifiers are present.
   *
   * @param serviceName name of the producer service (e.g., patra-ingest)
   * @param businessType logical business category (e.g., literature_batch)
   * @param businessId unique identifier supplied by the caller
   * @param correlationData optional structured metadata for downstream searches
   */
  public BusinessContext {
    if (serviceName == null || serviceName.isBlank()) {
      throw new IllegalArgumentException("Service name cannot be blank");
    }
    if (businessType == null || businessType.isBlank()) {
      throw new IllegalArgumentException("Business type cannot be blank");
    }
    if (businessId == null || businessId.isBlank()) {
      throw new IllegalArgumentException("Business ID cannot be blank");
    }
    correlationData = sanitize(correlationData);
  }

  private static Map<String, Object> sanitize(Map<String, Object> source) {
    if (source == null || source.isEmpty()) {
      return Map.of();
    }
    Map<String, Object> copy = new LinkedHashMap<>(source.size());
    source.forEach(
        (key, value) ->
            copy.put(Objects.requireNonNull(key, "Correlation data key cannot be null"), value));
    return Collections.unmodifiableMap(copy);
  }
}
