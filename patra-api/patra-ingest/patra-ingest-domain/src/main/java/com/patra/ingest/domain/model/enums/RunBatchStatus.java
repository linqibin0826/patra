package com.patra.ingest.domain.model.enums;

import com.patra.common.enums.CodeEnum;
import lombok.Getter;

/**
 * 运行批次状态
 */
@Getter
public enum RunBatchStatus implements CodeEnum<String> {
    RUNNING("running", "执行中"),
    SUCCEEDED("succeeded", "成功"),
    FAILED("failed", "失败"),
    SKIPPED("skipped", "已跳过");

    private final String code;
    private final String description;

    RunBatchStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
