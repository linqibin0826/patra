package com.patra.ingest.domain.model.enums;

import lombok.Getter;

/**
 * Ingestion operation type (DICT: ing_operation).
 *
 * <p><b>Persistence mapping</b>
 *
 * <ul>
 *   <li>ing_plan.operation_code → HARVEST/BACKFILL/UPDATE/METRICS
 *   <li>ing_task.operation_code → HARVEST/BACKFILL/UPDATE/METRICS
 *   <li>ing_cursor.operation_code → HARVEST/BACKFILL/UPDATE/METRICS
 *   <li>ing_cursor_event.operation_code → HARVEST/BACKFILL/UPDATE/METRICS
 * </ul>
 *
 * <p><b>Parsing/output contract</b>
 *
 * <ul>
 *   <li>Always emit uppercase values via {@link #getCode()}.
 *   <li>Parse using {@link #fromCode(String)} which trims and uppercases; unknown values raise
 *       {@link IllegalArgumentException}.
 * </ul>
 *
 * <p>Extension strategy: update upstream configuration and dictionary tables when adding new
 * operation types to remain backward compatible.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Getter
public enum OperationCode {
  /** Initial full ingestion (first run or rebuilt windows). */
  HARVEST("HARVEST", "Full ingestion"),
  /** Historical backfill to close gaps or correct data. */
  BACKFILL("BACKFILL", "Backfill ingestion"),
  /** Incremental updates driven by cursor progression. */
  UPDATE("UPDATE", "Incremental update"),
  /** Metrics/statistics-oriented operations (read-heavy). */
  METRICS("METRICS", "Metrics collection");

  private final String code;
  private final String description;

  OperationCode(String code, String description) {
    this.code = code;
    this.description = description;
  }

  /**
   * Parse the provided code into the enumeration.
   *
   * @param value string code (e.g., {@code "harvest"} or {@code " UPDATE "})
   * @return matching enum
   * @throws IllegalArgumentException when the value is null or unknown
   */
  public static OperationCode fromCode(String value) {
    if (value == null) {
      throw new IllegalArgumentException("Operation code cannot be null");
    }
    String normalized = value.trim().toUpperCase();
    for (OperationCode oc : values()) {
      if (oc.code.equals(normalized)) {
        return oc;
      }
    }
    throw new IllegalArgumentException("Unknown operation code: " + value);
  }
}
