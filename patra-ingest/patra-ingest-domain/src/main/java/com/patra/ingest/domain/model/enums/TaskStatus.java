package com.patra.ingest.domain.model.enums;

import lombok.Getter;

/**
 * 任务状态（DICT：ing_task_status）。
 * <p>字段映射：ing_task.status_code → QUEUED/RUNNING/SUCCEEDED/FAILED/CANCELLED</p>
 * <p>状态流转示例：QUEUED → RUNNING → SUCCEEDED｜FAILED｜CANCELLED</p>
 */
@Getter
public enum TaskStatus {
    /**
     * 等待执行（已入队未被消费）
     */
    QUEUED("QUEUED", "排队中"),
    /**
     * 消费执行中（不可重复启动）
     */
    RUNNING("RUNNING", "运行中"),
    /**
     * 成功完成
     */
    SUCCEEDED("SUCCEEDED", "成功"),
    /**
     * 执行失败（可触发补偿或重试策略）
     */
    FAILED("FAILED", "失败"),
    /**
     * 部分批次失败
     */
    PARTIAL("PARTIAL", "部分失败"),
    /**
     * 批次全部成功但游标推进失败，等待异步重试
     */
    CURSOR_PENDING("CURSOR_PENDING", "游标推进待重试"),
    /**
     * 被主动终止或条件不满足取消
     */
    CANCELLED("CANCELLED", "已取消");

    private final String code;
    private final String description;

    TaskStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static TaskStatus fromCode(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Task status code cannot be null");
        }
        String n = value.trim().toUpperCase();
        for (TaskStatus e : values()) {
            if (e.code.equals(n)) return e;
        }
        throw new IllegalArgumentException("Unknown task status code: " + value);
    }
}
