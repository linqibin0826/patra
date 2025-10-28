package com.patra.starter.provenance.common.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;

/**
 * Batch metadata required by data source adapters.
 *
 * @param batchNo sequential batch number within the execution run
 * @param query original query string before source-specific compilation
 * @param params parameters used when issuing the upstream API call
 * @param cursorToken resume cursor supplied by the upstream data source
 * @param expectedCount expected number of items in this batch if known
 */
@Builder
@Jacksonized
public record BatchInfo(
    int batchNo, String query, JsonNode params, String cursorToken, Integer expectedCount) {

  /**
   * Creates a copy with overridden cursor token.
   *
   * @param newCursorToken new cursor token
   * @return immutable batch info with updated cursor
   */
  public BatchInfo withCursorToken(String newCursorToken) {
    return new BatchInfo(batchNo, query, params, newCursorToken, expectedCount);
  }
}
