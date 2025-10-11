package com.patra.ingest.domain.model.enums;

import lombok.Getter;

/**
 * Scheduler source enumeration (DICT: ing_scheduler).
 *
 * <p>Persistence conventions</p>
 * <ul>
 *   <li>Column: <b>scheduler_code</b></li>
 *   <li>Location: {@code ing_schedule_instance.scheduler_code}</li>
 *   <li>Definition: {@code VARCHAR(32) NOT NULL DEFAULT 'XXL'} with comment “DICT CODE(type=ing_scheduler)”</li>
 *   <li>Stored value: {@link #getCode()} (for example, {@code "XXL"})</li>
 * </ul>
 *
 * <p>Values and semantics</p>
 * <ul>
 *   <li>XXL — XXL-Job scheduler (external distributed center)</li>
 *   <li>SPRING — Spring in-application scheduler (@Scheduled)</li>
 *   <li>QUARTZ — Quartz scheduler (application or cluster scoped)</li>
 * </ul>
 *
 * <p>Conversion contract</p>
 * <ul>
 *   <li>Emit codes via {@link #getCode()}.</li>
 *   <li>Parse using {@link #fromCode(String)}; trimming and uppercasing; unknown values raise {@link IllegalArgumentException}.</li>
 * </ul>
 *
 * <p>Evolution guardrails</p>
 * <ul>
 *   <li>Keep the <b>ing_scheduler</b> dictionary, defaults, validation, and documentation in sync when adding values.</li>
 *   <li>Changing defaults requires DDL/migration and seed-data updates.</li>
 * </ul>
 *
 * <p>Layer placement: domain enumeration with no framework dependencies.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Getter
public enum Scheduler {

    /**
     * XXL-Job scheduler (external distributed center).
     */
    XXL("XXL", "XXL-Job scheduler"),
    /**
     * Spring in-application scheduled tasks.
     */
    SPRING("SPRING", "Spring scheduled tasks"),
    /**
     * Quartz scheduler (application or cluster scoped).
     */
    QUARTZ("QUARTZ", "Quartz scheduler");

    /**
     * Dictionary code persisted to {@code scheduler_code}.
     */
    private final String code;
    /**
     * Human-readable description for display or documentation.
     */
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
