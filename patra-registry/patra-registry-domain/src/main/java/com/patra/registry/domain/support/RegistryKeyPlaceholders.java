package com.patra.registry.domain.support;

/**
 * Registry 维度/条件 Key 的占位符常量集合。
 * <p>
 * 统一描述 SOURCE/TASK 等作用域在快照或配置合并时使用的保留字，避免各层重复定义。
 * </p>
 *
 * <p>参考：docs/patra-registry/expr/Registry-expr-schema-design.md</p>
 */
public final class RegistryKeyPlaceholders {

    private RegistryKeyPlaceholders() {
    }

    /**
     * 表示“全部任务/来源”，用于作用域合并时的兜底 Key。
     */
    public static final String ALL = "ALL";

    /**
     * 表示“任意/未指定”，应用于匹配类型、值类型等可空字段。
     */
    public static final String ANY = "ANY";

    /**
     * 表示规则取反为 true 的规范化标记。
     */
    public static final String NEGATED_TRUE = "T";

    /**
     * 表示规则未取反（或显式为 false）的规范化标记。
     */
    public static final String NEGATED_FALSE = "F";
}
