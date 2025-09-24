package com.patra.ingest.domain.model.enums;

import lombok.Getter;

/**
 * 采集操作类型（DICT：ing_operation）。
 *
 * <p>持久化字段（参考 V0.1.0__init_ingest_schema.sql）</p>
 * <ul>
 *   <li>ing_plan.operation_code —— HARVEST/BACKFILL/UPDATE/METRICS</li>
 *   <li>ing_task.operation_code —— HARVEST/BACKFILL/UPDATE/METRICS</li>
 *   <li>ing_cursor.operation_code —— HARVEST/BACKFILL/UPDATE/METRICS</li>
 *   <li>ing_cursor_event.operation_code —— HARVEST/BACKFILL/UPDATE/METRICS</li>
 * </ul>
 *
 * <p>转换约定（domain 无框架依赖）</p>
 * <ul>
 *   <li>输出编码：{@link #getCode()}（大写）</li>
 *   <li>解析编码：{@link #fromCode(String)} 忽略大小写/首尾空白；未知抛出 IllegalArgumentException</li>
 * </ul>
 *
 * <p>分层：domain</p>
 *
 * author linqibin @since 0.1.0
 */
@Getter
public enum OperationCode {
    HARVEST("HARVEST", "全量采集"),
    BACKFILL("BACKFILL", "回灌补采"),
    UPDATE("UPDATE", "增量更新"),
    METRICS("METRICS", "指标统计");

    private final String code;
    private final String description;

    OperationCode(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 解析编码为枚举。
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
