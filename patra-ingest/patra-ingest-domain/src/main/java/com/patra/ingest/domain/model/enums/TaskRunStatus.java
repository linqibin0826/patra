package com.patra.ingest.domain.model.enums;

import lombok.Getter;

/**
 * Task run (attempt) status (DICT: ing_task_run_status).
 *
 * <p>Field mapping: {@code ing_task_run.status_code →
 * PLANNED/RUNNING/SUCCEEDED/FAILED/PARTIAL/CURSOR_PENDING}
 *
 * <p>Status transitions and semantics:
 *
 * <ul>
 *   <li><b>PLANNED</b> - Initial state when TaskRun is created; awaiting execution start
 *   <li><b>RUNNING</b> - Task execution started (via {@code start()} method)
 *   <li><b>SUCCEEDED</b> - Task completed successfully with all records processed (via {@code
 *       succeed()} method)
 *   <li><b>FAILED</b> - Task failed with errors, no records processed (via {@code fail()} method)
 *   <li><b>PARTIAL</b> - Task partially succeeded; some records processed, some failed (via {@code
 *       markPartial()} method)
 *   <li><b>CURSOR_PENDING</b> - Cursor-based pagination in progress, awaiting next token for
 *       continuation (via {@code markCursorPending()} method)
 * </ul>
 */
@Getter
public enum TaskRunStatus {
  PLANNED("PLANNED", "Planned"),
  RUNNING("RUNNING", "Running"),
  SUCCEEDED("SUCCEEDED", "Succeeded"),
  FAILED("FAILED", "Failed"),
  PARTIAL("PARTIAL", "Partially failed"),
  CURSOR_PENDING("CURSOR_PENDING", "Cursor pending");

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
