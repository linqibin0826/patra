package com.patra.ingest.domain.model.enums;

import com.patra.common.enums.CodeEnum;
import lombok.Getter;

/**
 * 水位命名空间
 */
@Getter
public enum NamespaceScope implements CodeEnum<String> {
    GLOBAL("global", "全局命名空间"),
    EXPR("expr", "按表达式隔离"),
    CUSTOM("custom", "自定义命名空间");

    private final String code;
    private final String description;

    NamespaceScope(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
