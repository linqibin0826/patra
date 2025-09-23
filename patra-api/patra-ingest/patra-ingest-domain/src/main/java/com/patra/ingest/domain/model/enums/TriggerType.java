package com.patra.ingest.domain.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 触发器类型枚举。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Getter
public enum TriggerType {
    
    SCHEDULE("SCHEDULE", "定时调度"),
    MANUAL("MANUAL", "手动触发"),
    API("API", "API调用");
    
    private final String code;
    private final String description;
    
    TriggerType(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    @JsonCreator
    public static TriggerType fromJson(String value) {
        if (value == null) {
            return SCHEDULE; // 默认值
        }
        String normalized = value.trim().toUpperCase();
        for (TriggerType type : values()) {
            if (type.code.equals(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown trigger type: " + value);
    }
    
    @JsonValue
    public String toJson() {
        return this.code;
    }
}
