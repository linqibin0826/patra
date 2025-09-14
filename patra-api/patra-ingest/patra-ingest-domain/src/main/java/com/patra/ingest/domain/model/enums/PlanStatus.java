package com.patra.ingest.domain.model.enums;

import com.patra.common.enums.CodeEnum;
import lombok.Getter;

/**
 * 计划状态
 */
@Getter
public enum PlanStatus implements CodeEnum<String> {
    DRAFT("draft", "草稿"),
    READY("ready", "就绪"),
    ACTIVE("active", "执行中"),
    COMPLETED("completed", "已完成"),
    ABORTED("aborted", "已终止");

    private final String code;
    private final String description;

    PlanStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
