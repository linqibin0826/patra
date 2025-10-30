package com.patra.ingest.domain.model.enums;

import lombok.Getter;

/**
 * Task status (DICT: ing_task_status).
 *
 * <p>Field mapping: {@code ing_task.status_code → PENDING/QUEUED/RUNNING/SUCCEEDED/FAILED}
 *
 * <p>State machine semantics:
 *
 * <ul>
 *   <li>PENDING → initial state, awaiting scheduler pickup
 *   <li>QUEUED → added to execution queue
 *   <li>RUNNING → TaskRun in progress
 *   <li>RUNNING ⇄ QUEUED → retry on failure (create new TaskRun)
 *   <li>SUCCEEDED → final success state (at least one TaskRun succeeded)
 *   <li>FAILED → final failure state (all TaskRuns failed, max retries reached)
 * </ul>
 *
 * <p><b>Note:</b> PARTIAL status moved to TaskRun layer for resumable execution tracking.
 */
@Getter
public enum TaskStatus {
  /** Pending; initial state, awaiting scheduler pickup. */
  PENDING("PENDING", "Pending"),
  /** Queued; added to execution queue, awaiting TaskRun creation. */
  QUEUED("QUEUED", "Queued"),
  /** Running; TaskRun in progress. */
  RUNNING("RUNNING", "Running"),
  /** Succeeded; at least one TaskRun completed successfully. */
  SUCCEEDED("SUCCEEDED", "Succeeded"),
  /** Failed; all TaskRuns failed, max retries reached. */
  FAILED("FAILED", "Failed");

  private final String code;
  private final String description;

  TaskStatus(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static TaskStatus fromCode(String value) {
    if (value == null) {
      throw new IllegalArgumentException("Task status code cannot be null");
    }
    String n = value.trim().toUpperCase();
    for (TaskStatus e : values()) {
      if (e.code.equals(n)) return e;
    }
    throw new IllegalArgumentException("Unknown task status code: " + value);
  }
}
