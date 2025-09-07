package com.patra.ingest.domain.model.enums;

import com.patra.common.enums.CodeEnum;
import lombok.Getter;

/**
 * 触发类型枚举
 *
 * @author linqibin
 * @since 0.1.0
 */
public enum TriggerType implements CodeEnum<String> {
    
    MANUAL("manual", "手工触发"),
    SCHEDULE("schedule", "定时触发"),
    REPLAY("replay", "重放触发");
    
    private final String code;
    @Getter
    private final String description;
    
    TriggerType(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    @Override
    public String getCode() {
        return code;
    }
}
