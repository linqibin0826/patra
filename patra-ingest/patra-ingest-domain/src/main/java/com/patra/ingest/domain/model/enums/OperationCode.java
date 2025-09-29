package com.patra.ingest.domain.model.enums;

import lombok.Getter;

/**
 * 采集操作类型（DICT：ing_operation）。
 * <p><b>持久化字段映射</b></p>
 * <ul>
 *   <li>ing_plan.operation_code → HARVEST/BACKFILL/UPDATE/METRICS</li>
 *   <li>ing_task.operation_code → HARVEST/BACKFILL/UPDATE/METRICS</li>
 *   <li>ing_cursor.operation_code → HARVEST/BACKFILL/UPDATE/METRICS</li>
 *   <li>ing_cursor_event.operation_code → HARVEST/BACKFILL/UPDATE/METRICS</li>
 * </ul>
 * <p><b>解析与输出约定</b></p>
 * <ul>
 *   <li>输出统一使用大写 {@link #getCode()}</li>
 *   <li>解析使用 {@link #fromCode(String)} 忽略大小写与首尾空白；未知值抛出 IllegalArgumentException</li>
 * </ul>
 * <p>扩展策略：新增操作类型需同步更新上游配置与字典表；保持向后兼容。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Getter
public enum OperationCode {
    /**
     * 初始全量采集（通常为首次或窗口重建）
     */
    HARVEST("HARVEST", "全量采集"),
    /**
     * 历史数据回补（弥补缺口或修正）
     */
    BACKFILL("BACKFILL", "回灌补采"),
    /**
     * 增量更新（基于游标推进）
     */
    UPDATE("UPDATE", "增量更新"),
    /**
     * 指标/统计类操作（读取型，无数据主写）
     */
    METRICS("METRICS", "指标统计");

    private final String code;
    private final String description;

    OperationCode(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 解析编码为枚举。
     *
     * @param value 字符串编码（如 "harvest"/" UPDATE ")
     * @return 对应枚举
     * @throws IllegalArgumentException 当为空或不识别时抛出
     */
    public static OperationCode fromCode(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Operation code cannot be null");
        }
        String normalized = value.trim().toUpperCase();
        for (OperationCode oc : values()) {
            if (oc.code.equals(normalized)) {
                return oc;
            }
        }
        throw new IllegalArgumentException("Unknown operation code: " + value);
    }
}
