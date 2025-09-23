package com.patra.ingest.domain.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 调度器代码枚举。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Getter
public enum SchedulerCode {
    
    XXL("XXL", "XXL-Job调度器"),
    SPRING("SPRING", "Spring定时任务"),
    QUARTZ("QUARTZ", "Quartz调度器");
    
    private final String code;
    private final String description;
    
    SchedulerCode(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    @JsonCreator
    public static SchedulerCode fromJson(String value) {
        if (value == null) {
            return XXL; // 默认值
        }
        String normalized = value.trim().toUpperCase();
        for (SchedulerCode type : values()) {
            if (type.code.equals(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown scheduler code: " + value);
    }
    
    @JsonValue
    public String toJson() {
        return this.code;
    }
}
