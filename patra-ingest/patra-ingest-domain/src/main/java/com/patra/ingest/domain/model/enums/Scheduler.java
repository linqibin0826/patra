package com.patra.ingest.domain.model.enums;

import lombok.Getter;

/**
 * Scheduler source enumeration (DICT: ing_scheduler).
 *
 * <p>Persistence conventions
 *
 * <ul>
 *   <li>Column: <b>scheduler_code</b>
 *   <li>Location: {@code ing_schedule_instance.scheduler_code}
 *   <li>Definition: {@code VARCHAR(32) NOT NULL DEFAULT 'XXL'} with comment “DICT
 *       CODE(type=ing_scheduler)”
 *   <li>Stored value: {@link #getCode()} (for example, {@code "XXL"})
 * </ul>
 *
 * <p>Values and semantics
 *
 * <ul>
 *   <li>XXL — XXL-Job scheduler (external distributed center)
 *   <li>SPRING — Spring in-application scheduler (@Scheduled)
 *   <li>QUARTZ — Quartz scheduler (application or cluster scoped)
 * </ul>
 *
 * <p>Conversion contract
 *
 * <ul>
 *   <li>Emit codes via {@link #getCode()}.
 *   <li>Parse using {@link #fromCode(String)}; trimming and uppercasing; unknown values raise
 *       {@link IllegalArgumentException}.
 * </ul>
 *
 * <p>Evolution guardrails
 *
 * <ul>
 *   <li>Keep the <b>ing_scheduler</b> dictionary, defaults, validation, and documentation in sync
 *       when adding values.
 *   <li>Changing defaults requires DDL/migration and seed-data updates.
 * </ul>
 *
 * <p>Layer placement: domain enumeration with no framework dependencies.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Getter
public enum Scheduler {

  /** XXL-Job scheduler (external distributed center). */
  XXL("XXL", "XXL-Job scheduler"),
  /** Spring in-application scheduled tasks. */
  SPRING("SPRING", "Spring scheduled tasks"),
  /** Quartz scheduler (application or cluster scoped). */
  QUARTZ("QUARTZ", "Quartz scheduler");

  /** Dictionary code persisted to {@code scheduler_code}. */
  private final String code;

  /** Human-readable description for display or documentation. */
  private final String description;

  Scheduler(String code, String description) {
    this.code = code;
    this.description = description;
  }

  /**
   * Parse the dictionary code, ignoring case and surrounding whitespace.
   *
   * @param value string code such as {@code "XXL"}, {@code "spring"}, or {@code " Quartz "}
   * @return matching {@link Scheduler}
   * @throws IllegalArgumentException when the value is null or unrecognized
   */
  public static Scheduler fromCode(String value) {
    if (value == null) {
      throw new IllegalArgumentException("Scheduler code cannot be null");
    }
    String normalized = value.trim().toUpperCase();
    for (Scheduler type : values()) {
      if (type.code.equals(normalized)) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unknown scheduler code: " + value);
  }
}
