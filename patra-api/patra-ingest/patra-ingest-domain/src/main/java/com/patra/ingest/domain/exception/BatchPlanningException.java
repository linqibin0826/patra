package com.patra.ingest.domain.exception;

/**
 * Exception thrown when batch planning cannot proceed due to invalid inputs or upstream metadata
 * lookup failures.
 */
public class BatchPlanningException extends IngestException {

  public BatchPlanningException(String message) {
    super(message);
  }

  public BatchPlanningException(String message, Throwable cause) {
    super(message, cause);
  }
}
