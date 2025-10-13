package com.patra.ingest.domain.model.enums;

import lombok.Getter;

/**
 * Plan slice status (DICT: ing_slice_status).
 *
 * <p>Field mapping: {@code ing_plan_slice.status_code}.
 */
@Getter
public enum SliceStatus {
  PENDING("PENDING", "Pending"),
  DISPATCHED("DISPATCHED", "Dispatched"),
  EXECUTING("EXECUTING", "Executing"),
  SUCCEEDED("SUCCEEDED", "Succeeded"),
  FAILED("FAILED", "Failed"),
  PARTIAL("PARTIAL", "Partially succeeded"),
  CANCELLED("CANCELLED", "Cancelled");

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
