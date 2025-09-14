package com.patra.ingest.domain.model.enums;

import com.patra.common.enums.CodeEnum;
import lombok.Getter;

/**
 * 切片状态
 */
@Getter
public enum SliceStatus implements CodeEnum<String> {
    PENDING("pending", "待调度"),
    DISPATCHED("dispatched", "已下发"),
    EXECUTING("executing", "执行中"),
    SUCCEEDED("succeeded", "成功"),
    FAILED("failed", "失败"),
    PARTIAL("partial", "部分成功"),
    CANCELLED("cancelled", "已取消");

    private final String code;
    private final String description;

    SliceStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
