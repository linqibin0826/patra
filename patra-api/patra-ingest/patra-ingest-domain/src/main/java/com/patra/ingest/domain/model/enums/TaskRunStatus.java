package com.patra.ingest.domain.model.enums;

import lombok.Getter;

/**
 * Task run (attempt) status (DICT: ing_task_run_status).
 *
 * <p>Field mapping: {@code ing_task_run.status_code → PENDING/RUNNING/SUCCEEDED/FAILED/PARTIAL}
 *
 * <p>Status transitions and semantics:
 *
 * <ul>
 *   <li><b>PENDING</b> - Initial state when TaskRun is created; awaiting execution start
 *   <li><b>RUNNING</b> - Task execution started (via {@code start()} method)
 *   <li><b>SUCCEEDED</b> - Task completed successfully with all records processed (via {@code
 *       succeed()} method)
 *   <li><b>FAILED</b> - Task failed with errors, no records processed (via {@code fail()} method)
 *   <li><b>PARTIAL</b> - Task partially succeeded; some batches processed, checkpoint saved for
 *       resumption (via {@code markPartial()} method) - <b>enables resumable execution</b>
 * </ul>
 *
 * <p><b>Note:</b> This is the ONLY layer that retains PARTIAL status for checkpoint-based resumable
 * execution.
 */
@Getter
public enum TaskRunStatus {
  /** Pending; initial state, awaiting execution start. */
  PENDING("PENDING", "Pending"),
  /** Running; task execution in progress. */
  RUNNING("RUNNING", "Running"),
  /** Succeeded; all records processed successfully. */
  SUCCEEDED("SUCCEEDED", "Succeeded"),
  /** Failed; task failed with errors, no records processed. */
  FAILED("FAILED", "Failed"),
  /** Partial; some batches processed, checkpoint saved for resumption. */
  PARTIAL("PARTIAL", "Partially completed");

  private final String code;
  private final String description;

  TaskRunStatus(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static TaskRunStatus fromCode(String value) {
    if (value == null) {
      throw new IllegalArgumentException("TaskRun status code cannot be null");
    }
    String n = value.trim().toUpperCase();
    for (TaskRunStatus e : values()) {
      if (e.code.equals(n)) return e;
    }
    throw new IllegalArgumentException("Unknown TaskRun status code: " + value);
  }
}
