package com.patra.ingest.domain.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;

/**
 * 计划状态（DICT：ing_plan_status）。
 * <p>字段映射：ing_plan.status_code → DRAFT/SLICING/READY/PARTIAL/FAILED/COMPLETED</p>
 * <p>状态机语义：</p>
 * <ul>
 *   <li>DRAFT → 初始创建（尚未切片）</li>
 *   <li>SLICING → 正在生成切片/任务</li>
 *   <li>READY → 切片与任务全部生成成功</li>
 *   <li>PARTIAL → 部分生成成功（允许补偿重试）</li>
 *   <li>FAILED → 装配失败或关键持久化失败</li>
 *   <li>COMPLETED → 运行生命周期闭合（全部任务完成）</li>
 * </ul>
 */
@Getter
public enum PlanStatus {
    /**
     * 草稿（尚未进入切片阶段）
     */
    DRAFT("DRAFT", "草稿"),
    /**
     * 切片进行中（不可重复进入）
     */
    SLICING("SLICING", "切片中"),
    /**
     * 切片生成完毕，可调度任务
     */
    READY("READY", "就绪"),
    /**
     * 部分生成成功（后续可能补偿）
     */
    PARTIAL("PARTIAL", "部分完成"),
    /**
     * 失败（需要人工或系统补偿）
     */
    FAILED("FAILED", "失败"),
    /**
     * 全生命周期完成（统计收敛状态）
     */
    COMPLETED("COMPLETED", "已完成");

    private final String code;
    private final String description;

    PlanStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonCreator
    public static PlanStatus fromCode(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Plan status code cannot be null");
        }
        String n = value.trim().toUpperCase();
        for (PlanStatus e : values()) {
            if (e.code.equals(n)) return e;
        }
        throw new IllegalArgumentException("Unknown plan status code: " + value);
    }
}
