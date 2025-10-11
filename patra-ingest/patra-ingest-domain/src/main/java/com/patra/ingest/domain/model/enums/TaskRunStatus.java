package com.patra.ingest.domain.model.enums;

import lombok.Getter;

/** Task run (attempt) status (DICT: ing_task_run_status).
 * <p>Field mapping: {@code ing_task_run.status_code → PLANNED/RUNNING/SUCCEEDED/FAILED/CANCELLED}</p>
 */
@Getter
public enum TaskRunStatus {
    PLANNED("PLANNED", "Planned"),
    RUNNING("RUNNING", "Running"),
    SUCCEEDED("SUCCEEDED", "Succeeded"),
    FAILED("FAILED", "Failed"),
    PARTIAL("PARTIAL", "Partially failed"),
    CURSOR_PENDING("CURSOR_PENDING", "Cursor pending"),
    CANCELLED("CANCELLED", "Cancelled");

    private final String code; private final String description;
    TaskRunStatus(String code, String description){ this.code=code; this.description=description; }

    public static TaskRunStatus fromCode(String value){
        if(value==null){ throw new IllegalArgumentException("TaskRun status code cannot be null"); }
        String n=value.trim().toUpperCase();
        for(TaskRunStatus e:values()){ if(e.code.equals(n)) return e; }
        throw new IllegalArgumentException("Unknown TaskRun status code: "+value);
    }
}
