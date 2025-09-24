package com.patra.ingest.domain.model.enums;

import lombok.Getter;

/** 采集端点（非DICT，字段：endpoint_name）。
 * <p>目前仅 SEARCH；如扩展请同步注释与使用点。</p>
 */
@Getter
public enum Endpoint {
    SEARCH("SEARCH", "搜索端点");

    private final String code; private final String description;
    Endpoint(String code, String description){ this.code=code; this.description=description; }

    public static Endpoint fromCode(String value){
        if(value==null){ throw new IllegalArgumentException("Endpoint code cannot be null"); }
        String n=value.trim().toUpperCase();
        for(Endpoint e:values()){ if(e.code.equals(n)) return e; }
        throw new IllegalArgumentException("Unknown endpoint code: "+value);
    }
}
