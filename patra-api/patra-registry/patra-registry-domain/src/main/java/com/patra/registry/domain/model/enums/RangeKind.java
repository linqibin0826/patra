package com.patra.registry.domain.model.enums;

import com.patra.common.enums.CodeEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 范围类型枚举：不支持、日期、日期时间、数值。
 * <p>用于 {@code QueryCapability} 声明 RANGE 操作时的值域类型。
 */
@Getter
@RequiredArgsConstructor
public enum RangeKind implements CodeEnum<String> {
    
    NONE("NONE", "无范围支持"),
    DATE("DATE", "日期范围"),
    DATETIME("DATETIME", "日期时间范围"),
    NUMBER("NUMBER", "数值范围");
    
    private final String code;
    private final String description;
}
