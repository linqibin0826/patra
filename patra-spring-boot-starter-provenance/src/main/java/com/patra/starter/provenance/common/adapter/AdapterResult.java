package com.patra.starter.provenance.common.adapter;

import com.patra.common.model.StandardLiterature;
import java.util.List;
import java.util.Objects;

/**
 * Result wrapper returned by {@link DataSourceAdapter} implementations.
 *
 * <p>The result captures success state, payload, cursor hints, and error classification so the
 * ingest engine can implement nuanced retry and failure policies.
 *
 * @param success whether the adapter completed without terminal errors
 * @param literatures immutable list of standardized literature records
 * @param nextCursorToken cursor token returned by the upstream API
 * @param errorMessage human readable diagnostic message, typically populated on failure
 * @param fetchedCount number of records fetched or attempted, used for metrics
 * @param errorType classification of the error, guiding retry semantics
 */
public record AdapterResult(
    boolean success,
    List<StandardLiterature> literatures,
    String nextCursorToken,
    String errorMessage,
    int fetchedCount,
    ErrorType errorType) {

  public AdapterResult {
    literatures = literatures == null ? List.of() : List.copyOf(literatures);
    fetchedCount = Math.max(fetchedCount, literatures.size());
    errorType = Objects.requireNonNullElse(errorType, ErrorType.NONE);
  }

  /**
   * Creates a successful result.
   *
   * @param literatures payload returned by the upstream data source
   * @param nextCursorToken cursor token for subsequent requests
   * @return immutable success result
   */
  public static AdapterResult success(
      List<StandardLiterature> literatures, String nextCursorToken) {
    return new AdapterResult(
        true,
        literatures,
        nextCursorToken,
        null,
        literatures == null ? 0 : literatures.size(),
        ErrorType.NONE);
  }

  /**
   * Creates a retriable failure result, signaling the caller to attempt a retry.
   *
   * @param errorMessage diagnostic message
   * @return retriable failure result
   */
  public static AdapterResult retriableFailure(String errorMessage) {
    return new AdapterResult(false, List.of(), null, errorMessage, 0, ErrorType.RETRIABLE);
  }

  /**
   * Creates a non-retriable failure result.
   *
   * @param errorMessage diagnostic message
   * @return terminal failure result
   */
  public static AdapterResult nonRetriableFailure(String errorMessage) {
    return new AdapterResult(false, List.of(), null, errorMessage, 0, ErrorType.NON_RETRIABLE);
  }

  /**
   * Creates a partial success result when some items succeed but warnings need recording.
   *
   * @param literatures successfully converted payload
   * @param nextCursorToken cursor token for continuation
   * @param warningMessage warning details
   * @param totalAttempted total items processed (including failures)
   * @return partial success result
   */
  public static AdapterResult partialSuccess(
      List<StandardLiterature> literatures,
      String nextCursorToken,
      String warningMessage,
      int totalAttempted) {
    return new AdapterResult(
        true,
        literatures,
        nextCursorToken,
        warningMessage,
        totalAttempted,
        ErrorType.PARTIAL_SUCCESS);
  }

  /**
   * Indicates whether the failure is retriable.
   *
   * @return true if the adapter suggests retrying the operation
   */
  public boolean isRetriable() {
    return errorType == ErrorType.RETRIABLE;
  }

  /** Error types reported by adapters. */
  public enum ErrorType {
    /** No error. */
    NONE,
    /** Transient error, caller should retry respecting backoff rules. */
    RETRIABLE,
    /** Terminal error, caller should not retry automatically. */
    NON_RETRIABLE,
    /** Partial success with warnings; caller may log and continue. */
    PARTIAL_SUCCESS
  }
}
