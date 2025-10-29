package com.patra.starter.provenance.common.adapter;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Complete parameters for batch execution.
 *
 * <p>Contains all information needed by adapters to construct upstream API requests:
 *
 * <ul>
 *   <li>Base parameters from task compilation (e.g., datetype, sort)
 *   <li>Pagination parameters (e.g., retstart, retmax)
 *   <li>Runtime state (e.g., WebEnv, query_key for PubMed)
 * </ul>
 *
 * @param query compiled query string for this batch execution
 * @param params complete parameter payload (base + pagination + runtime state)
 */
public record BatchExecutionParams(String query, JsonNode params) {

  /**
   * Validates invariants when creating the record.
   *
   * @param query compiled query string (can be null for some data sources)
   * @param params complete parameter payload
   */
  public BatchExecutionParams {
    // query can be null for some data sources (e.g., date-range only queries)
  }
}
