package com.patra.ingest.domain.model.enums;

import com.patra.common.enums.CodeEnum;
import lombok.Getter;

/**
 * 切片状态枚举
 *
 * @author linqibin
 * @since 0.1.0
 */
public enum SliceStatus implements CodeEnum<String> {
    
    PENDING("pending", "待处理"),
    DISPATCHED("dispatched", "已分发"),
    EXECUTING("executing", "执行中"),
    SUCCEEDED("succeeded", "已成功"),
    FAILED("failed", "已失败"),
    PARTIAL("partial", "部分成功");
    
    private final String code;
    @Getter
    private final String description;
    
    SliceStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    @Override
    public String getCode() {
        return code;
    }

}
