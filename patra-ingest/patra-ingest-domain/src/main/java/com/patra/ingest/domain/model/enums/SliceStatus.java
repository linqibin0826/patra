package com.patra.ingest.domain.model.enums;

import lombok.Getter;

/**
 * Plan slice status (DICT: ing_slice_status).
 *
 * <p>Field mapping: {@code ing_plan_slice.status_code → PENDING/ASSIGNED/FINISHED}
 *
 * <p>State machine semantics (enforces 1:1 Slice-Task relationship):
 *
 * <ul>
 *   <li>PENDING → awaiting Task generation
 *   <li>ASSIGNED → corresponding Task has been created (1:1 mapping)
 *   <li>FINISHED → associated Task reached terminal state (SUCCEEDED or FAILED)
 * </ul>
 *
 * <p><b>Note:</b> Slice does not distinguish success/failure. Query the associated Task for
 * execution results.
 */
@Getter
public enum SliceStatus {
  /** Pending; awaiting Task generation. */
  PENDING("PENDING", "Pending"),
  /** Assigned; corresponding Task has been created. */
  ASSIGNED("ASSIGNED", "Assigned"),
  /** Finished; associated Task reached terminal state. */
  FINISHED("FINISHED", "Finished");

  private final String code;
  private final String description;

  SliceStatus(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static SliceStatus fromCode(String value) {
    if (value == null) {
      throw new IllegalArgumentException("Slice status code cannot be null");
    }
    String n = value.trim().toUpperCase();
    for (SliceStatus e : values()) {
      if (e.code.equals(n)) return e;
    }
    throw new IllegalArgumentException("Unknown slice status code: " + value);
  }
}
