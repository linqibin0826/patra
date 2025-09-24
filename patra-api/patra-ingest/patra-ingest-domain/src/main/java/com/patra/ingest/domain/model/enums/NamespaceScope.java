package com.patra.ingest.domain.model.enums;

import lombok.Getter;

/** 命名空间作用域（DICT：ing_namespace_scope）。
 * <p>用于区分游标命名空间：GLOBAL/EXPR/CUSTOM；映射字段 namespace_scope_code。</p>
 */
@Getter
public enum NamespaceScope {
    GLOBAL("GLOBAL", "全局命名空间"),
    EXPR("EXPR", "表达式哈希命名空间"),
    CUSTOM("CUSTOM", "自定义命名空间");

    private final String code; private final String description;
    NamespaceScope(String code, String description){ this.code=code; this.description=description; }

    public static NamespaceScope fromCode(String value){
        if(value==null){ throw new IllegalArgumentException("Namespace scope code cannot be null"); }
        String n=value.trim().toUpperCase();
        for(NamespaceScope e:values()){ if(e.code.equals(n)) return e; }
        throw new IllegalArgumentException("Unknown namespace scope code: "+value);
    }
}
