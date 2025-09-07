package com.patra.ingest.domain.model.enums;

import com.patra.common.enums.CodeEnum;
import lombok.Getter;

/**
 * 任务类型枚举
 *
 * @author linqibin
 * @since 0.1.0
 */
public enum JobType implements CodeEnum<String> {
    
    HARVEST("harvest", "采集"),
    BACKFILL("backfill", "回填"),
    UPDATE("update", "增量更新"),
    METRICS("metrics", "外部计量");
    
    private final String code;
    @Getter
    private final String description;
    
    JobType(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    @Override
    public String getCode() {
        return code;
    }

}
