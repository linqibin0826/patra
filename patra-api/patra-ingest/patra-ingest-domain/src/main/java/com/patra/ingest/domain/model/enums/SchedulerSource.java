package com.patra.ingest.domain.model.enums;

import com.patra.common.enums.CodeEnum;
import lombok.Getter;

/**
 * 调度器来源
 */
@Getter
public enum SchedulerSource implements CodeEnum<String> {
    XXL("xxl", "XXL-Job"),
    MANUAL("manual", "人工/本地触发"),
    OTHER("other", "其他调度器");

    private final String code;
    private final String description;

    SchedulerSource(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
