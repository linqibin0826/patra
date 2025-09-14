package com.patra.ingest.domain.model.enums;

import com.patra.common.enums.CodeEnum;
import lombok.Getter;

/**
 * 任务状态
 */
@Getter
public enum TaskStatus implements CodeEnum<String> {
    QUEUED("queued", "排队中"),
    RUNNING("running", "执行中"),
    SUCCEEDED("succeeded", "成功"),
    FAILED("failed", "失败"),
    CANCELLED("cancelled", "已取消");

    private final String code;
    private final String description;

    TaskStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
