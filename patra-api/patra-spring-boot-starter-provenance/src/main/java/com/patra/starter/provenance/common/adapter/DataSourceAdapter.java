package com.patra.starter.provenance.common.adapter;

/**
 * Unified contract implemented by provenance data source adapters.
 *
 * <p>The ingest engine only depends on this interface, enabling new data sources to be introduced
 * by providing additional implementations without modifying existing ingestion logic.
 */
public interface DataSourceAdapter {

  /**
   * Returns the provenance code (e.g. {@code pubmed}) served by this adapter.
   *
   * @return unique provenance code
   */
  String getProvenanceCode();

  /**
   * Checks whether the adapter supports the specified operation.
   *
   * @param operationCode operation identifier such as HARVEST or UPDATE
   * @return true when the adapter can execute the operation
   */
  boolean supports(String operationCode);

  /**
   * Executes the data retrieval and conversion workflow.
   *
   * @param request immutable request payload originating from the ingest engine
   * @return result describing the outcome, payload, and retry guidance
   */
  AdapterResult fetchData(AdapterRequest request);
}
