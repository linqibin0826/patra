package com.patra.ingest.domain.model.enums;

import com.patra.common.enums.CodeEnum;
import lombok.Getter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 采集类型
 */
@Getter
public enum IngestOperationType implements CodeEnum<String> {
    HARVEST("harvest", "采集"),
    BACKFILL("backfill", "回填"),
    UPDATE("update", "增量/更新"),
    METRICS("metrics", "外部计量");

    private final String code;
    private final String description;

    IngestOperationType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonCreator
    public static IngestOperationType fromCode(String code) {
        for (IngestOperationType type : IngestOperationType.values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown code: " + code);
    }

    @JsonValue
    public String toCode() {
        return this.code;
    }
}
