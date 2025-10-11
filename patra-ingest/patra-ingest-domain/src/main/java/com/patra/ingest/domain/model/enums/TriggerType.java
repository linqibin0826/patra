package com.patra.ingest.domain.model.enums;

import lombok.Getter;

/** Trigger type (DICT: ing_trigger_type).
 * <p>Field mapping: {@code ing_schedule_instance.trigger_type_code → SCHEDULE/MANUAL/API}</p>
 */
@Getter
public enum TriggerType {

    SCHEDULE("SCHEDULE", "Scheduled trigger"),
    MANUAL("MANUAL", "Manual trigger"),
    API("API", "API invocation");

    private final String code;
    private final String description;

    TriggerType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static TriggerType fromCode(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Trigger type code cannot be null");
        }
        String normalized = value.trim().toUpperCase();
        for (TriggerType type : values()) {
            if (type.code.equals(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown trigger type: " + value);
    }
}
