package com.patra.registry.domain.model.enums;

import com.patra.common.enums.CodeEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 平台字段数据类型枚举。
 * <p>用于 {@code PlatformFieldDict} 与渲染/校验逻辑，约束字段的原子类型。
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
