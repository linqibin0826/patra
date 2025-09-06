package com.patra.registry.domain.model.enums;

import com.patra.common.enums.CodeEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 基数枚举
 * docref: /docs/domain/enums.discovery.md#2-cardinality-基数枚举
 */
@Getter
@RequiredArgsConstructor
public enum Cardinality implements CodeEnum<String> {
    
    SINGLE("single", "单值"),
    MULTI("multi", "多值");
    
    private final String code;
    private final String description;
}
