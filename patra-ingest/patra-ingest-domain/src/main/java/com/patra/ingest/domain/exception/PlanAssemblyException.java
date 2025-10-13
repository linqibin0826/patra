package com.patra.ingest.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;
import java.util.EnumSet;
import java.util.Set;

/**
 * Exception thrown when plan assembly fails.
 *
 * <p>Occurs after validation succeeds but the slicing/task generation stages cannot produce
 * executable batches. Unlike {@link PlanValidationException}, inputs are valid; the failure stems
 * from assembly algorithms or combination rules.
 *
 * <p>Handling strategy:
 *
 * <ul>
 *   <li>{@link Reason#EMPTY_RESULT}: treat as an empty window, log at INFO, and stop creation.
 *   <li>{@link Reason#SLICE_GENERATION_FAILED} / {@link Reason#TASK_GENERATION_FAILED}: log at
 *       ERROR and inspect the strategy implementation or baseline data.
 * </ul>
 */
public class PlanAssemblyException extends IngestException implements HasErrorTraits {

  /**
   * Root cause for assembly failure.
   *
   * <p>Helps callers select log levels and alerting strategies.
   */
  public enum Reason {
    /** Algorithm succeeded but produced an empty result (no data in the window). */
    EMPTY_RESULT,
    /** Failed while generating slices (errors in window partitioning or boundary calculations). */
    SLICE_GENERATION_FAILED,
    /** Failed while generating tasks (slice-to-task mapping or parameter assembly issues). */
    TASK_GENERATION_FAILED
  }

  /** Reason provided for the failure; may be {@code null} when not distinguished. */
  private final Reason reason;

  /**
   * Create the exception with a message and no specific reason.
   *
   * @param message descriptive message
   */
  public PlanAssemblyException(String message) {
    this(message, null, null);
  }

  /**
   * Create the exception with a message and reason.
   *
   * @param message descriptive message
   * @param reason failure reason
   */
  public PlanAssemblyException(String message, Reason reason) {
    this(message, reason, null);
  }

  /**
   * Create the exception with a message, reason, and underlying cause.
   *
   * @param message descriptive message
   * @param reason failure reason
   * @param cause underlying cause
   */
  public PlanAssemblyException(String message, Reason reason, Throwable cause) {
    super(message, cause);
    this.reason = reason;
  }

  /**
   * Create the exception with a message and underlying cause without specifying the reason.
   *
   * @param message descriptive message
   * @param cause underlying cause
   */
  public PlanAssemblyException(String message, Throwable cause) {
    this(message, null, cause);
  }

  /**
   * Expose the failure reason.
   *
   * @return reason, possibly {@code null}
   */
  public Reason getReason() {
    return reason;
  }

  @Override
  public Set<ErrorTrait> getErrorTraits() {
    return EnumSet.of(ErrorTrait.RULE_VIOLATION);
  }
}
