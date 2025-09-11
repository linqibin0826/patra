package com.patra.registry.domain.model.enums;

import com.patra.common.enums.CodeEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 基数字段取值枚举：单值或多值。
 * <p>用于 {@code PlatformFieldDict} 声明字段是否可出现多个值。
 * @author linqibin
 * @since 0.1.0
 */
@Getter
@RequiredArgsConstructor
public enum Cardinality implements CodeEnum<String> {
    
    SINGLE("single", "单值"),
    MULTI("multi", "多值");
    
    private final String code;
    private final String description;
}
