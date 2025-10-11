package com.patra.ingest.domain.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;

/**
 * Plan status (DICT: ing_plan_status).
 * <p>Field mapping: {@code ing_plan.status_code → DRAFT/SLICING/READY/PARTIAL/FAILED/COMPLETED}</p>
 * <p>State machine semantics:</p>
 * <ul>
 *   <li>DRAFT → newly created, slicing not started</li>
 *   <li>SLICING → slices/tasks are being generated</li>
 *   <li>READY → slices and tasks created successfully</li>
 *   <li>PARTIAL → partially generated, compensation may proceed</li>
 *   <li>FAILED → assembly or critical persistence failed</li>
 *   <li>COMPLETED → lifecycle closed, all tasks finished</li>
 * </ul>
 */
@Getter
public enum PlanStatus {
    /** Draft; slicing has not started yet. */
    DRAFT("DRAFT", "Draft"),
    /** Slicing in progress (non-repeatable transition). */
    SLICING("SLICING", "Slicing"),
    /** Slices generated and tasks ready for scheduling. */
    READY("READY", "Ready"),
    /** Partially successful; additional compensation may occur. */
    PARTIAL("PARTIAL", "Partially completed"),
    /** Failed and requires manual/system recovery. */
    FAILED("FAILED", "Failed"),
    /** Fully completed lifecycle. */
    COMPLETED("COMPLETED", "Completed");

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
