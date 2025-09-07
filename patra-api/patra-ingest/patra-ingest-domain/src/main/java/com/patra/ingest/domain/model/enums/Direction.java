package com.patra.ingest.domain.model.enums;

import com.patra.common.enums.CodeEnum;
import lombok.Getter;

/**
 * 推进方向枚举
 *
 * @author linqibin
 * @since 0.1.0
 */
public enum Direction implements CodeEnum<String> {
    
    FORWARD("forward", "前进增量"),
    BACKFILL("backfill", "历史回灌");
    
    private final String code;
    @Getter
    private final String description;
    
    Direction(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    @Override
    public String getCode() {
        return code;
    }
}
