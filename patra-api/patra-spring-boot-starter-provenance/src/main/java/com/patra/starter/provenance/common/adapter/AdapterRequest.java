package com.patra.starter.provenance.common.adapter;

import com.patra.starter.provenance.common.config.ProvenanceConfig;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;

/**
 * Immutable request passed to {@link DataSourceAdapter} implementations.
 *
 * <p><strong>Design rationale:</strong>
 *
 * <ul>
 *   <li>{@code executionParams}: Everything needed to construct upstream API requests (query +
 *       complete parameters including pagination)
 *   <li>{@code metadata}: Batch identity and cursor state for logging and resumption
 *   <li>{@code config}: Runtime configuration for HTTP, retry, rate limiting
 * </ul>
 *
 * @param operationCode ingest operation (e.g., HARVEST, UPDATE)
 * @param config merged configuration applied for this execution
 * @param executionParams batch execution parameters (query + complete params)
 * @param metadata batch metadata (batchNo, cursor, expectedCount)
 */
@Builder
@Jacksonized
public record AdapterRequest(
    String operationCode,
    ProvenanceConfig config,
    BatchExecutionParams executionParams,
    BatchMetadata metadata) {

  /**
   * Validates invariants when creating the record.
   *
   * @param operationCode ingest operation code
   * @param config runtime configuration
   * @param executionParams batch execution parameters
   * @param metadata batch metadata
   */
  public AdapterRequest {
    if (operationCode == null || operationCode.isBlank()) {
      throw new IllegalArgumentException("operationCode must be provided");
    }
    if (executionParams == null) {
      throw new IllegalArgumentException("executionParams must be provided");
    }
    if (metadata == null) {
      throw new IllegalArgumentException("metadata must be provided");
    }
  }
}
