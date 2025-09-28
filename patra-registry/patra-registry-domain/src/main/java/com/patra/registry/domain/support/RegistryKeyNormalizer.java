package com.patra.registry.domain.support;

import java.util.Locale;

/**
 * Registry 维度/条件 Key 的规范化工具。
 * <p>
 * 在仓储与快照装载流程中统一处理大小写、占位符与空值语义，保证不同来源数据能够正确合并。
 * </p>
 */
public final class RegistryKeyNormalizer {

    private RegistryKeyNormalizer() {
    }

    /**
     * 任务类型 Key 规范化：null/blank → ALL，其余 trim，保留原大小写以兼容既有约定。
     */
    public static String normalizeTaskKey(String taskType) {
        if (taskType == null || taskType.isBlank()) {
            return RegistryKeyPlaceholders.ALL;
        }
        return taskType.trim();
    }

    /**
     * 通用 code 规范化：非空断言 + trim + 转大写。
     */
    public static String normalizeCode(String value) {
        if (value == null) {
            throw new IllegalArgumentException("value 不能为空");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 字段 Key 规范化：非空断言 + trim，保留原大小写。
     */
    public static String normalizeFieldKey(String value) {
        if (value == null) {
            throw new IllegalArgumentException("value 不能为空");
        }
        return value.trim();
    }

    /**
     * 匹配类型 Key 规范化：null/blank → ANY，其余大写。
     */
    public static String normalizeMatchKey(String matchTypeCode) {
        if (matchTypeCode == null || matchTypeCode.isBlank()) {
            return RegistryKeyPlaceholders.ANY;
        }
        return matchTypeCode.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 取反标记规范化：null → ANY，true → T，false → F。
     */
    public static String normalizeNegatedKey(Boolean negated) {
        if (negated == null) {
            return RegistryKeyPlaceholders.ANY;
        }
        return negated ? RegistryKeyPlaceholders.NEGATED_TRUE : RegistryKeyPlaceholders.NEGATED_FALSE;
    }

    /**
     * 值类型 Key 规范化：null/blank → ANY，其余大写。
     */
    public static String normalizeValueKey(String valueTypeCode) {
        if (valueTypeCode == null || valueTypeCode.isBlank()) {
            return RegistryKeyPlaceholders.ANY;
        }
        return valueTypeCode.trim().toUpperCase(Locale.ROOT);
    }
}
