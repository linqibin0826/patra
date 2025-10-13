package com.patra.ingest.app.outbox.core;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Result of an Outbox publishing operation.
 *
 * <p>Encapsulates success/failure counts, error details, and duration metrics.
 *
 * @author linqibin
 * @since 0.1.0
 */
public final class OutboxPublishResult {

  private final int successCount;
  private final int failureCount;
  private final List<FailureDetail> failures;
  private final Duration duration;

  private OutboxPublishResult(
      int successCount, int failureCount, List<FailureDetail> failures, Duration duration) {
    this.successCount = successCount;
    this.failureCount = failureCount;
    this.failures = Collections.unmodifiableList(Objects.requireNonNull(failures));
    this.duration = Objects.requireNonNull(duration);
  }

  /**
   * Returns the number of successfully published messages.
   *
   * @return Success count
   */
  public int getSuccessCount() {
    return successCount;
  }

  /**
   * Returns the number of failed messages.
   *
   * @return Failure count
   */
  public int getFailureCount() {
    return failureCount;
  }

  /**
   * Returns the list of failure details.
   *
   * @return Unmodifiable list of failures (never null, may be empty)
   */
  public List<FailureDetail> getFailures() {
    return failures;
  }

  /**
   * Returns the operation duration.
   *
   * @return Duration (never null)
   */
  public Duration getDuration() {
    return duration;
  }

  /**
   * Checks if there were any failures.
   *
   * @return true if at least one message failed, false otherwise
   */
  public boolean hasFailures() {
    return failureCount > 0;
  }

  /**
   * Creates a successful result.
   *
   * @param count Number of successfully published messages
   * @param duration Operation duration
   * @return Success result
   */
  public static OutboxPublishResult success(int count, Duration duration) {
    return new OutboxPublishResult(count, 0, Collections.emptyList(), duration);
  }

  /**
   * Creates a failure result.
   *
   * @param errorMessage General error message
   * @param duration Operation duration
   * @return Failure result
   */
  public static OutboxPublishResult failure(String errorMessage, Duration duration) {
    FailureDetail detail = new FailureDetail(null, null, errorMessage, "GENERAL_ERROR");
    return new OutboxPublishResult(0, 1, List.of(detail), duration);
  }

  /**
   * Creates a partial failure result (some succeeded, some failed).
   *
   * @param successCount Number of successful messages
   * @param failures List of failure details
   * @param duration Operation duration
   * @return Partial failure result
   */
  public static OutboxPublishResult partial(
      int successCount, List<FailureDetail> failures, Duration duration) {
    return new OutboxPublishResult(successCount, failures.size(), failures, duration);
  }

  /**
   * Creates an empty result (no messages to process).
   *
   * @param duration Operation duration
   * @return Empty result
   */
  public static OutboxPublishResult empty(Duration duration) {
    return new OutboxPublishResult(0, 0, Collections.emptyList(), duration);
  }

  /**
   * Merges two results (useful for batch processing).
   *
   * @param other Other result to merge
   * @return Merged result
   */
  public OutboxPublishResult merge(OutboxPublishResult other) {
    int totalSuccess = this.successCount + other.successCount;
    int totalFailure = this.failureCount + other.failureCount;

    List<FailureDetail> mergedFailures = new java.util.ArrayList<>(this.failures);
    mergedFailures.addAll(other.failures);

    Duration totalDuration = this.duration.plus(other.duration);

    return new OutboxPublishResult(totalSuccess, totalFailure, mergedFailures, totalDuration);
  }

  /**
   * Detailed failure information for a single message.
   *
   * @param aggregateId Aggregate ID (nullable)
   * @param dedupKey Deduplication key (nullable)
   * @param errorMessage Error message
   * @param errorType Error type (e.g., "VALIDATION_ERROR", "DB_ERROR", "JSON_ERROR")
   */
  public record FailureDetail(
      String aggregateId, String dedupKey, String errorMessage, String errorType) {}

  @Override
  public String toString() {
    return String.format(
        "OutboxPublishResult{success=%d, failure=%d, duration=%dms}",
        successCount, failureCount, duration.toMillis());
  }
}
