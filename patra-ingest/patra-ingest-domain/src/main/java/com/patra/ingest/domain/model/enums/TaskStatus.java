package com.patra.ingest.domain.model.enums;

import lombok.Getter;

/**
 * Task status (DICT: ing_task_status).
 * <p>Field mapping: {@code ing_task.status_code → QUEUED/RUNNING/SUCCEEDED/FAILED/CANCELLED}</p>
 * <p>Example transitions: {@code QUEUED → RUNNING → SUCCEEDED | FAILED | CANCELLED}</p>
 */
@Getter
public enum TaskStatus {
    /** Queued and awaiting execution. */
    QUEUED("QUEUED", "Queued"),
    /** Currently running (not idempotent to re-run). */
    RUNNING("RUNNING", "Running"),
    /** Completed successfully. */
    SUCCEEDED("SUCCEEDED", "Succeeded"),
    /** Failed execution (eligible for compensation or retry). */
    FAILED("FAILED", "Failed"),
    /** Partially failed batches. */
    PARTIAL("PARTIAL", "Partially failed"),
    /** Batches succeeded but cursor advancement pending retry. */
    CURSOR_PENDING("CURSOR_PENDING", "Cursor pending"),
    /** Cancelled by operator or unmet conditions. */
    CANCELLED("CANCELLED", "Cancelled");

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
