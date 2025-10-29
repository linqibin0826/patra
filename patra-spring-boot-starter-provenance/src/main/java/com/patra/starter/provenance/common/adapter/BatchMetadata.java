package com.patra.starter.provenance.common.adapter;

/**
 * Batch metadata for logging, monitoring, and cursor management.
 *
 * <p>This record serves as batch metadata only. For constructing API requests, use {@link
 * BatchExecutionParams} from {@link AdapterRequest}.
 *
 * @param batchNo sequential batch number within the execution run (1-based)
 * @param cursorToken resume cursor supplied by the upstream data source (nullable)
 */
public record BatchMetadata(int batchNo, String cursorToken) {

  /**
   * Validates invariants when creating the record.
   *
   * @param batchNo sequential batch number (must be >= 1)
   * @param cursorToken resume cursor token
   */
  public BatchMetadata {
    if (batchNo < 1) {
      throw new IllegalArgumentException("batchNo must be >= 1, got: " + batchNo);
    }
  }

  /**
   * Creates metadata for the first batch without cursor.
   *
   * @return metadata for batch #1 with no cursor
   */
  public static BatchMetadata first() {
    return new BatchMetadata(1, null);
  }

  /**
   * Creates metadata with updated cursor token.
   *
   * @param newCursorToken new cursor token
   * @return new metadata instance with updated cursor
   */
  public BatchMetadata withCursorToken(String newCursorToken) {
    return new BatchMetadata(batchNo, newCursorToken);
  }
}
