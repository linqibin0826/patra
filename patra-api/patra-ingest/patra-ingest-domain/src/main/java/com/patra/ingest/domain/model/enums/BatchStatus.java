package com.patra.ingest.domain.model.enums;

import lombok.Getter;

/**
 * 运行批次状态（DICT：ing_batch_status）。
 * <p>字段映射：ing_task_run_batch.status_code → RUNNING/SUCCEEDED/FAILED/SKIPPED</p>
 */
@Getter
public enum BatchStatus {
    RUNNING("RUNNING", "运行中"),
    SUCCEEDED("SUCCEEDED", "成功"),
    FAILED("FAILED", "失败"),
    SKIPPED("SKIPPED", "跳过");

    private final String code;
    private final String description;

    BatchStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static BatchStatus fromCode(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Batch status code cannot be null");
        }
        String n = value.trim().toUpperCase();
        for (BatchStatus e : values()) {
            if (e.code.equals(n)) return e;
        }
        throw new IllegalArgumentException("Unknown batch status code: " + value);
    }
}
