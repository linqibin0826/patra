package com.patra.registry.domain.model.enums;

import com.patra.common.enums.CodeEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 值类型枚举
 * docref: /docs/domain/enums.discovery.md#7-valuetype-值类型枚举
 */
@Getter
@RequiredArgsConstructor
public enum ValueType implements CodeEnum<String> {
    
    STRING("string", "字符串类型"),
    DATE("date", "日期类型"),
    DATETIME("datetime", "日期时间类型"),
    NUMBER("number", "数值类型");
    
    private final String code;
    private final String description;
}
