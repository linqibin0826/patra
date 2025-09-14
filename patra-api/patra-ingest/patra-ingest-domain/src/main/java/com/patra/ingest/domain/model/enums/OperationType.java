package com.patra.ingest.domain.model.enums;

import com.patra.common.enums.CodeEnum;
import lombok.Getter;

/**
 * 任务操作类型
 */
@Getter
public enum OperationType implements CodeEnum<String> {
    HARVEST("harvest", "采集"),
    BACKFILL("backfill", "回填"),
    UPDATE("update", "增量/更新"),
    METRICS("metrics", "外部计量");

    private final String code;
    private final String description;

    OperationType(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
