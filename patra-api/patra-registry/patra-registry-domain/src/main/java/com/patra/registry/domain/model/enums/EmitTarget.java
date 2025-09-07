package com.patra.registry.domain.model.enums;

import com.patra.common.enums.CodeEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 渲染目标枚举
 * docref: /docs/domain/enums.discovery.md#8-emittarget-渲染目标枚举
 */
@Getter
@RequiredArgsConstructor
public enum EmitTarget implements CodeEnum<String> {
    
    QUERY("query", "输出到查询字符串"),
    PARAMS("params", "输出到参数对象");
    
    private final String code;
    private final String description;
}
