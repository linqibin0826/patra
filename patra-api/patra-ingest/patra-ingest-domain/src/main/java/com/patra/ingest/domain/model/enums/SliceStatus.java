package com.patra.ingest.domain.model.enums;

import lombok.Getter;

/** 计划切片状态（DICT：ing_slice_status）。
 * <p>字段映射：ing_plan_slice.status_code。</p>
 */
@Getter
public enum SliceStatus {
    PENDING("PENDING", "待处理"),
    DISPATCHED("DISPATCHED", "已下发"),
    EXECUTING("EXECUTING", "执行中"),
    SUCCEEDED("SUCCEEDED", "成功"),
    FAILED("FAILED", "失败"),
    PARTIAL("PARTIAL", "部分成功"),
    CANCELLED("CANCELLED", "已取消");

    private final String code; private final String description;
    SliceStatus(String code, String description){ this.code=code; this.description=description; }

    public static SliceStatus fromCode(String value){
        if(value==null){ throw new IllegalArgumentException("Slice status code cannot be null"); }
        String n=value.trim().toUpperCase();
        for(SliceStatus e:values()){ if(e.code.equals(n)) return e; }
        throw new IllegalArgumentException("Unknown slice status code: "+value);
    }
}
