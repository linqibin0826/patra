package com.patra.ingest.domain.model.enums;

import lombok.Getter;

/** 游标推进方向（DICT：ing_cursor_direction）。
 * <p>字段映射：ing_cursor_event.direction_code → FORWARD/BACKFILL。</p>
 */
@Getter
public enum CursorDirection {
    FORWARD("FORWARD", "向前推进"),
    BACKFILL("BACKFILL", "回灌回溯");

    private final String code; private final String description;
    CursorDirection(String code, String description){ this.code=code; this.description=description; }

    public static CursorDirection fromCode(String value){
        if(value==null){ throw new IllegalArgumentException("Cursor direction code cannot be null"); }
        String n=value.trim().toUpperCase();
        for(CursorDirection e:values()){ if(e.code.equals(n)) return e; }
        throw new IllegalArgumentException("Unknown cursor direction code: "+value);
    }
}
