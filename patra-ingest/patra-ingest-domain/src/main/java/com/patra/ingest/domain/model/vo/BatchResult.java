package com.patra.ingest.domain.model.vo;

/**
 * Value object describing the outcome of a batch execution.
 * <p>Captures success flag, row counts, cursor token, error message, and storage key.</p>
 * <p>Invariants:
 * <ul>
 *   <li>{@code batchNo} >= 1</li>
 *   <li>{@code fetchedCount} >= 0</li>
 *   <li>When {@code success} is {@code false}, {@code errorMessage} must be present</li>
 * </ul>
 * </p>
 *
 * @param batchNo         batch sequence number
 * @param success         success flag
 * @param fetchedCount    number of records fetched
 * @param nextCursorToken cursor token for the next batch
 * @param errorMessage    error details when {@code success} is false
 * @param storageKey      storage location (e.g., object storage path)
 * @author linqibin
 * @since 0.1.0
 */
public record BatchResult(
    int batchNo,
    boolean success,
    int fetchedCount,
    String nextCursorToken,
    String errorMessage,
    String storageKey
) {
    public BatchResult {
        if (batchNo < 1) {
            throw new IllegalArgumentException("batchNo must be >= 1");
        }
        if (fetchedCount < 0) {
            throw new IllegalArgumentException("fetchedCount must not be negative");
        }
        if (!success && (errorMessage == null || errorMessage.isBlank())) {
            throw new IllegalArgumentException("errorMessage must be provided when success is false");
        }
    }

    /**
     * Factory for a successful batch result.
     */
    public static BatchResult success(int batchNo, int fetchedCount, String nextCursorToken, String storageKey) {
        return new BatchResult(batchNo, true, fetchedCount, nextCursorToken, null, storageKey);
    }

    /**
     * Factory for a failed batch result.
     */
    public static BatchResult failure(int batchNo, String errorMessage) {
        return new BatchResult(batchNo, false, 0, null, errorMessage, null);
    }

    /**
     * Returns {@code true} when a cursor token is available for further pagination.
     */
    public boolean hasNextCursor() {
        return nextCursorToken != null && !nextCursorToken.isBlank();
    }
}
