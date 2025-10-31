package com.patra.ingest.domain.model.vo.batch;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Value object representing a single execution batch.
 *
 * <p>Encapsulates the compiled query, parameters, and pagination metadata.
 *
 * <p>Invariants:
 *
 * <ul>
 *   <li>{@code batchNo} >= 1
 *   <li>{@code query} must not be blank
 * </ul>
 *
 * <p>Pagination modes:
 *
 * <ul>
 *   <li><b>Page-based</b>: uses {@code pageNo} and {@code pageSize} (e.g., PubMed retstart/retmax)
 *   <li><b>Token-based</b>: uses {@code cursorToken} and optional {@code pageSize} (e.g., EPMC
 *       cursorMark)
 * </ul>
 *
 * @param batchNo batch sequence number (1-based)
 * @param query compiled query string
 * @param params query parameters as JSON
 * @param cursorToken cursor token for token-based pagination (nullable)
 * @param pageNo page number for page-based pagination (1-based, nullable for token-based)
 * @param pageSize page size or expected fetch count (nullable)
 * @author linqibin
 * @since 0.1.0
 */
public record Batch(
    int batchNo,
    String query,
    JsonNode params,
    String cursorToken,
    Integer pageNo,
    Integer pageSize) {
  public Batch {
    if (batchNo < 1) {
      throw new IllegalArgumentException("batchNo must be >= 1");
    }
  }

  /** Create the first batch without pagination metadata (legacy). */
  public static Batch first(String query, JsonNode params) {
    return new Batch(1, query, params, null, null, null);
  }

  /** Create a page-based batch (e.g., PubMed with retstart/retmax). */
  public static Batch withPage(
      int batchNo, String query, JsonNode params, int pageNo, int pageSize) {
    return new Batch(batchNo, query, params, null, pageNo, pageSize);
  }

  /** Create a token-based batch (e.g., EPMC with cursorMark). */
  public static Batch withToken(
      int batchNo, String query, JsonNode params, String cursorToken, Integer pageSize) {
    return new Batch(batchNo, query, params, cursorToken, null, pageSize);
  }

  /** Indicates whether a cursor token is present. */
  public boolean hasCursor() {
    return cursorToken != null && !cursorToken.isBlank();
  }
}
