package com.patra.ingest.domain.model.enums;

import com.patra.common.enums.CodeEnum;
import lombok.Getter;

/**
 * 切片策略
 */
@Getter
public enum SliceStrategy implements CodeEnum<String> {
    TIME("time", "时间窗口切片"),
    ID_RANGE("id_range", "ID 区间切片"),
    CURSOR_LANDMARK("cursor_landmark", "游标里程碑切片"),
    VOLUME_BUDGET("volume_budget", "体量预算切片"),
    HYBRID("hybrid", "混合切片策略");

    private final String code;
    private final String description;

    SliceStrategy(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
