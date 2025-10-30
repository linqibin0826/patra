package com.patra.ingest.domain.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;

/**
 * Plan status (DICT: ing_plan_status).
 *
 * <p>Field mapping: {@code ing_plan.status_code → DRAFT/SLICING/READY/ARCHIVED}
 *
 * <p>State machine semantics:
 *
 * <ul>
 *   <li>DRAFT → newly created, slicing not started
 *   <li>SLICING → slices/tasks are being generated
 *   <li>READY → slices and tasks created successfully
 *   <li>ARCHIVED → lifecycle closed, all tasks finished (previously COMPLETED)
 * </ul>
 *
 * <p><b>Note:</b> Plan status only reflects its own lifecycle. Execution results (partial/failed)
 * should be queried by aggregating Task status.
 */
@Getter
public enum PlanStatus {
  /** Draft; slicing has not started yet. */
  DRAFT("DRAFT", "Draft"),
  /** Slicing in progress (non-repeatable transition). */
  SLICING("SLICING", "Slicing"),
  /** Slices generated and tasks ready for scheduling. */
  READY("READY", "Ready"),
  /** Archived; lifecycle closed, all tasks finished. */
  ARCHIVED("ARCHIVED", "Archived");

  private final String code;
  private final String description;

  PlanStatus(String code, String description) {
    this.code = code;
    this.description = description;
  }

  @JsonCreator
  public static PlanStatus fromCode(String value) {
    if (value == null) {
      throw new IllegalArgumentException("Plan status code cannot be null");
    }
    String n = value.trim().toUpperCase();
    for (PlanStatus e : values()) {
      if (e.code.equals(n)) return e;
    }
    throw new IllegalArgumentException("Unknown plan status code: " + value);
  }
}
