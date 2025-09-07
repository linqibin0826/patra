package com.patra.ingest.domain.model.enums;

import com.patra.common.enums.CodeEnum;

/**
 * 批次状态枚举
 *
 * @author linqibin
 * @since 0.1.0
 */
public enum BatchStatus implements CodeEnum<String> {
    
    RUNNING("running", "运行中"),
    SUCCEEDED("succeeded", "已成功"),
    FAILED("failed", "已失败"),
    SKIPPED("skipped", "已跳过");
    
    private final String code;
    private final String description;
    
    BatchStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    @Override
    public String getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
}
