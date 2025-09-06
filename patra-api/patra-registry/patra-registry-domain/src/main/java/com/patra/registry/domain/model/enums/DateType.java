package com.patra.registry.domain.model.enums;

import com.patra.common.enums.CodeEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 日期类型枚举
 * docref: /docs/domain/enums.discovery.md#3-datetype-日期类型枚举
 */
@Getter
@RequiredArgsConstructor
public enum DateType implements CodeEnum<String> {
    
    PDAT("PDAT", "Publication Date", "发表日期"),
    EDAT("EDAT", "Entrez Date", "入库日期"),
    MHDA("MHDA", "MeSH Date", "MeSH索引日期");
    
    private final String code;
    private final String name;
    private final String description;
}
