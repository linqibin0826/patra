package com.patra.registry.domain.model.enums;

import com.patra.common.enums.CodeEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 查询渲染输出目标：拼接到 query 字符串或 params 参数表。
 */
@Getter
@RequiredArgsConstructor
public enum EmitTarget implements CodeEnum<String> {
    
    QUERY("query", "输出到查询字符串"),
    PARAMS("params", "输出到参数对象");
    
    private final String code;
    private final String description;
}
