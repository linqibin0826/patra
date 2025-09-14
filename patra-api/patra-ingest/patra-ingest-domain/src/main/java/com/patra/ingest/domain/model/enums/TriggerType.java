package com.patra.ingest.domain.model.enums;

import com.patra.common.enums.CodeEnum;
import lombok.Getter;

/**
 * 触发类型
 */
@Getter
public enum TriggerType implements CodeEnum<String> {
    MANUAL("manual", "手动触发"),
    SCHEDULE("schedule", "定时调度"),
    REPLAY("replay", "回放重跑");

    private final String code;
    private final String description;

    TriggerType(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
