package com.patra.ingest.domain.model.enums;

import lombok.Getter;

/** 任务状态（DICT：ing_task_status）。
 * <p>字段映射：ing_task.status_code → QUEUED/RUNNING/SUCCEEDED/FAILED/CANCELLED</p>
 */
@Getter
public enum TaskStatus {
    QUEUED("QUEUED", "排队中"),
    RUNNING("RUNNING", "运行中"),
    SUCCEEDED("SUCCEEDED", "成功"),
    FAILED("FAILED", "失败"),
    CANCELLED("CANCELLED", "已取消");

    private final String code; private final String description;
    TaskStatus(String code, String description){ this.code=code; this.description=description; }

    public static TaskStatus fromCode(String value){
        if(value==null){ throw new IllegalArgumentException("Task status code cannot be null"); }
        String n=value.trim().toUpperCase();
        for(TaskStatus e:values()){ if(e.code.equals(n)) return e; }
        throw new IllegalArgumentException("Unknown task status code: "+value);
    }
}
