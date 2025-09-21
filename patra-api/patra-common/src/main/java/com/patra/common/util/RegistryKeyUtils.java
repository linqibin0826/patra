package com.patra.common.util;

import com.patra.common.constant.RegistryKeys;

import java.util.Locale;

/**
 * Registry 维度 / 条件 Key 归一化工具。
 * <p>统一在仓储层与快照聚合处使用，保证以下特性：
 * <ul>
 *     <li>大小写约束：所有 code 型（opCode / emitTypeCode 等）统一转大写，避免大小写重复。</li>
 *     <li>空值语义：对 scope / match / value / negated 等可选条件提供占位符（{@link RegistryKeys#ALL} / {@link RegistryKeys#ANY}）。</li>
 *     <li>组合 Key 稳定：减少 String 拼接歧义，便于缓存与 map 覆盖合并。</li>
 * </ul>
 * 设计原则：不在此处做业务校验，仅做“格式与占位”标准化。
 * </p>
 *
 * <p>参考：docs/patra-registry/expr/Registry-expr-schema-design.md 中的 Key 合并策略。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public final class RegistryKeyUtils {
    private RegistryKeyUtils() {
    }

    /** 任务类型规范化：空/blank → ALL，其余 trim（保持大小写以兼容原始 taskTypeKey）。 */
    public static String normalizeTaskKey(String taskType) {
        if (taskType == null || taskType.isBlank()) {
            return RegistryKeys.ALL;
        }
        return taskType.trim();
    }

    /** 通用 code 规范化：非空校验 + trim + 大写。 */
    public static String normalizeCode(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Value cannot be null");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    /** 字段 Key：仅非空校验 + trim，保留原大小写（字段命名大小写敏感）。 */
    public static String normalizeFieldKey(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Value cannot be null");
        }
        return value.trim();
    }

    /** 匹配类型：空/blank → ANY，非空 → 大写。 */
    public static String normalizeMatchKey(String matchTypeCode) {
        if (matchTypeCode == null || matchTypeCode.isBlank()) {
            return RegistryKeys.ANY;
        }
        return matchTypeCode.trim().toUpperCase(Locale.ROOT);
    }

    /** 取反标记：null → ANY（未指定），true → T，false → F。 */
    public static String normalizeNegatedKey(Boolean negated) {
        if (negated == null) {
            return RegistryKeys.ANY;
        }
        return negated ? RegistryKeys.NEGATED_TRUE : RegistryKeys.NEGATED_FALSE;
    }

    /** 值类型：空/blank → ANY，非空 → 大写。 */
    public static String normalizeValueKey(String valueTypeCode) {
        if (valueTypeCode == null || valueTypeCode.isBlank()) {
            return RegistryKeys.ANY;
        }
        return valueTypeCode.trim().toUpperCase(Locale.ROOT);
    }
}
