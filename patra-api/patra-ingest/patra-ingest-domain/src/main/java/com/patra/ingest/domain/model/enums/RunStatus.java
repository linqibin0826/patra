package com.patra.ingest.domain.model.enums;

import com.patra.common.enums.CodeEnum;
import lombok.Getter;

/**
 * 运行状态枚举
 *
 * @author linqibin
 * @since 0.1.0
 */
public enum RunStatus implements CodeEnum<String> {
    
    PLANNED("planned", "计划中"),
    RUNNING("running", "运行中"),
    SUCCEEDED("succeeded", "已成功"),
    FAILED("failed", "已失败"),
    CANCELLED("cancelled", "已取消");
    
    private final String code;
    @Getter
    private final String description;
    
    RunStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    @Override
    public String getCode() {
        return code;
    }

}
