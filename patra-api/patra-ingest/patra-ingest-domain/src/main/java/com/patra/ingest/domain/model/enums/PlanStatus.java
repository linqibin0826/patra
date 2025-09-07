package com.patra.ingest.domain.model.enums;

import com.patra.common.enums.CodeEnum;
import lombok.Getter;

/**
 * 计划状态枚举
 *
 * @author linqibin
 * @since 0.1.0
 */
public enum PlanStatus implements CodeEnum<String> {
    
    DRAFT("draft", "草稿"),
    READY("ready", "准备就绪"),
    ACTIVE("active", "执行中"),
    COMPLETED("completed", "已完成"),
    ABORTED("aborted", "已中止");
    
    private final String code;
    @Getter
    private final String description;
    
    PlanStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    @Override
    public String getCode() {
        return code;
    }

}
