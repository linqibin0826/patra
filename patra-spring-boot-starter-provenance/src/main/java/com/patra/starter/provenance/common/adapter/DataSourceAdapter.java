package com.patra.starter.provenance.common.adapter;

/**
 * Unified contract implemented by provenance data source adapters.
 *
 * <p>The ingest engine only depends on this interface, enabling new data sources to be introduced
 * by providing additional implementations without modifying existing ingestion logic.
 *
 * <p>Adapters are responsible solely for data retrieval and have no business logic awareness.
 * Operation types (HARVEST, UPDATE, etc.) are orchestration-level concerns handled by upper layers.
 */
public interface DataSourceAdapter {

  /**
   * Returns the provenance code (e.g. {@code pubmed}) served by this adapter.
   *
   * @return unique provenance code
   */
  String getProvenanceCode();

  /**
   * Executes the data retrieval and conversion workflow.
   *
   * @param request immutable request payload originating from the ingest engine
   * @return result describing the outcome, payload, and retry guidance
   */
  AdapterResult fetchData(AdapterRequest request);
}
