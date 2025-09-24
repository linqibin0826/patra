package com.patra.ingest.domain.model.enums;

import lombok.Getter;

/** 任务运行(Attempt)状态（DICT：ing_task_run_status）。
 * <p>字段映射：ing_task_run.status_code → PLANNED/RUNNING/SUCCEEDED/FAILED/CANCELLED</p>
 */
@Getter
public enum TaskRunStatus {
    PLANNED("PLANNED", "已规划"),
    RUNNING("RUNNING", "运行中"),
    SUCCEEDED("SUCCEEDED", "成功"),
    FAILED("FAILED", "失败"),
    CANCELLED("CANCELLED", "已取消");

    private final String code; private final String description;
    TaskRunStatus(String code, String description){ this.code=code; this.description=description; }

    public static TaskRunStatus fromCode(String value){
        if(value==null){ throw new IllegalArgumentException("TaskRun status code cannot be null"); }
        String n=value.trim().toUpperCase();
        for(TaskRunStatus e:values()){ if(e.code.equals(n)) return e; }
        throw new IllegalArgumentException("Unknown TaskRun status code: "+value);
    }
}
