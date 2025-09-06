package com.patra.registry.domain.enums;

import com.patra.common.enums.CodeEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 数据类型枚举
 * docref: /docs/domain/enums.discovery.md#1-datatype-数据类型枚举
 */
@Getter
@RequiredArgsConstructor
public enum DataType implements CodeEnum<String> {
    
    DATE("date", "日期类型"),
    DATETIME("datetime", "日期时间类型"), 
    NUMBER("number", "数值类型"),
    TEXT("text", "文本类型"),
    KEYWORD("keyword", "关键词类型"),
    BOOLEAN("boolean", "布尔类型"),
    TOKEN("token", "令牌类型");
    
    private final String code;
    private final String description;
}
