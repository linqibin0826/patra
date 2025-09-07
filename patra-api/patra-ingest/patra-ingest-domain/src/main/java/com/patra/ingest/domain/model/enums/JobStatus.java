package com.patra.ingest.domain.model.enums;

import com.patra.common.enums.CodeEnum;
import lombok.Getter;

/**
 * 任务状态枚举
 *
 * @author linqibin
 * @since 0.1.0
 */
public enum JobStatus implements CodeEnum<String> {

    QUEUED("queued", "排队中"),
    RUNNING("running", "执行中"),
    SUCCEEDED("succeeded", "已成功"),
    FAILED("failed", "已失败"),
    CANCELLED("cancelled", "已取消");

    private final String code;
    @Getter
    private final String description;

    JobStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @Override
    public String getCode() {
        return code;
    }

}
