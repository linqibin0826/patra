package com.patra.registry.domain.model.enums;

import com.patra.common.enums.CodeEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 规则渲染时的值类型提示（影响格式化与校验）。
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
