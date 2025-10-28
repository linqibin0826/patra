package com.patra.starter.provenance.common.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.provenance.common.config.ProvenanceConfig;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;

/**
 * Immutable request passed to {@link DataSourceAdapter} implementations.
 *
 * @param operationCode ingest operation (e.g. HARVEST, UPDATE)
 * @param config merged configuration applied for this execution
 * @param compiledQuery finalized query string, potentially provider-specific
 * @param compiledParams fully rendered parameter payload
 * @param batchInfo batch metadata extracted from the ingest engine
 */
@Builder
@Jacksonized
public record AdapterRequest(
    String operationCode,
    ProvenanceConfig config,
    String compiledQuery,
    JsonNode compiledParams,
    BatchInfo batchInfo) {

  /**
   * Validates invariants when creating the record.
   *
   * @param operationCode ingest operation code
   * @param config merged configuration
   * @param compiledQuery rendered query
   * @param compiledParams rendered parameters
   * @param batchInfo associated batch information
   */
  public AdapterRequest {
    if (operationCode == null || operationCode.isBlank()) {
      throw new IllegalArgumentException("operationCode must be provided");
    }
  }
}
