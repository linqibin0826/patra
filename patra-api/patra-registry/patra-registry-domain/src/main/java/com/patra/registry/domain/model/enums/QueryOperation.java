package com.patra.registry.domain.model.enums;

import com.patra.common.enums.CodeEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 查询操作枚举
 * docref: /docs/domain/enums.discovery.md#5-queryoperation-查询操作枚举
 */
@Getter
@RequiredArgsConstructor
public enum QueryOperation implements CodeEnum<String> {
    
    TERM("term", "词条查询"),
    IN("in", "包含查询"),
    RANGE("range", "范围查询"),
    EXISTS("exists", "存在性查询"),
    TOKEN("token", "令牌查询");
    
    private final String code;
    private final String description;
}
