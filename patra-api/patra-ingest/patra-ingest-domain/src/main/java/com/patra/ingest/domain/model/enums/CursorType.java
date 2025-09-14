package com.patra.ingest.domain.model.enums;

import com.patra.common.enums.CodeEnum;
import lombok.Getter;

/**
 * 游标类型
 */
@Getter
public enum CursorType implements CodeEnum<String> {
    TIME("time", "时间型游标"),
    ID("id", "递增ID型游标"),
    TOKEN("token", "不透明令牌游标");

    private final String code;
    private final String description;

    CursorType(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
