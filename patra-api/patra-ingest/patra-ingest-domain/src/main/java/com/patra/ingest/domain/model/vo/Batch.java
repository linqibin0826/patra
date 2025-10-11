package com.patra.ingest.domain.model.vo;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Value object representing a single execution batch.
 * <p>Encapsulates the compiled query, parameters, and optional cursor token.</p>
 * <p>Invariants:
 * <ul>
 *   <li>{@code batchNo} >= 1</li>
 *   <li>{@code query} must not be blank</li>
 * </ul>
 * </p>
 *
 * @param batchNo       batch sequence number (1-based)
 * @param query         compiled query string
 * @param params        query parameters as JSON
 * @param cursorToken   cursor token for pagination (nullable)
 * @param expectedCount expected number of rows (nullable)
 * @author linqibin
 * @since 0.1.0
 */
public record Batch(
    int batchNo,
    String query,
    JsonNode params,
    String cursorToken,
    Integer expectedCount
) {
    public Batch {
        if (batchNo < 1) {
            throw new IllegalArgumentException("batchNo must be >= 1");
        }
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
    }

    /**
     * Create the first batch without a cursor.
     */
    public static Batch first(String query, JsonNode params) {
        return new Batch(1, query, params, null, null);
    }

    /**
     * Create a subsequent batch using the provided cursor token.
     */
    public static Batch next(int batchNo, String query, JsonNode params, String cursorToken) {
        return new Batch(batchNo, query, params, cursorToken, null);
    }

    /**
     * Indicates whether a cursor token is present.
     */
    public boolean hasCursor() {
        return cursorToken != null && !cursorToken.isBlank();
    }
}
