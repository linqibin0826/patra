package com.patra.ingest.domain.model.enums;

import lombok.Getter;

/** 游标类型（DICT：ing_cursor_type）。
 * <p>字段映射：cursor_type_code → TIME/ID/TOKEN。</p>
 */
@Getter
public enum CursorType {
    TIME("TIME", "时间型"),
    ID("ID", "ID型"),
    TOKEN("TOKEN", "令牌型");

    private final String code; private final String description;
    CursorType(String code, String description){ this.code=code; this.description=description; }

    public static CursorType fromCode(String value){
        if(value==null){ throw new IllegalArgumentException("Cursor type code cannot be null"); }
        String n=value.trim().toUpperCase();
        for(CursorType e:values()){ if(e.code.equals(n)) return e; }
        throw new IllegalArgumentException("Unknown cursor type code: "+value);
    }
}
