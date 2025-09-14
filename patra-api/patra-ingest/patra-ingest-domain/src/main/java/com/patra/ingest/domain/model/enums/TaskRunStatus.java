package com.patra.ingest.domain.model.enums;

import com.patra.common.enums.CodeEnum;
import lombok.Getter;

/**
 * 任务运行状态（attempt）
 */
@Getter
public enum TaskRunStatus implements CodeEnum<String> {
    PLANNED("planned", "已计划"),
    RUNNING("running", "执行中"),
    SUCCEEDED("succeeded", "成功"),
    FAILED("failed", "失败"),
    CANCELLED("cancelled", "已取消");

    private final String code;
    private final String description;

    TaskRunStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
