package com.patra.ingest.domain.model.enums;

import lombok.Getter;

/** 计划状态（DICT：ing_plan_status）。
 * <p>字段映射：ing_plan.status_code → DRAFT/SLICING/READY/PARTIAL/FAILED/COMPLETED</p>
 */
@Getter
public enum PlanStatus {
    DRAFT("DRAFT", "草稿"),
    SLICING("SLICING", "切片中"),
    READY("READY", "就绪"),
    PARTIAL("PARTIAL", "部分完成"),
    FAILED("FAILED", "失败"),
    COMPLETED("COMPLETED", "已完成");

    private final String code; private final String description;
    PlanStatus(String code, String description){ this.code=code; this.description=description; }

    public static PlanStatus fromCode(String value){
        if(value==null){ throw new IllegalArgumentException("Plan status code cannot be null"); }
        String n=value.trim().toUpperCase();
        for(PlanStatus e:values()){ if(e.code.equals(n)) return e; }
        throw new IllegalArgumentException("Unknown plan status code: "+value);
    }
}
