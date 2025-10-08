package com.patra.ingest.adapter.inbound.stream.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * INGEST_TASK_READY message payload DTO (simplified).
 * <p>
 * Parses MQ message body (JSON format) for task ready events.
 * Contains only essential fields - all other business data should be queried from database.
 * </p>
 * <p>
 * Fields:
 * <ul>
 *   <li>taskId: Task ID (required, for context loading and lease acquisition)</li>
 *   <li>idempotentKey: Idempotent key (required, for deduplication)</li>
 * </ul>
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
public class TaskReadyPayload {

    /** Task ID */
    @JsonProperty("taskId")
    private Long taskId;

    /** Idempotent key */
    @JsonProperty("idempotentKey")
    private String idempotentKey;

    /**
     * Validates required fields.
     *
     * @throws IllegalArgumentException when required fields are null/blank
     */
    public void validate() {
        if (taskId == null) {
            throw new IllegalArgumentException("taskId must not be null");
        }
        if (idempotentKey == null || idempotentKey.isBlank()) {
            throw new IllegalArgumentException("idempotentKey must not be blank");
        }
    }
}
