package com.patra.common.constant;

/**
 * Registry 维度归一化占位/标记常量集合。
 * <p>
 * 这些常量主要用于：
 * <ul>
 *     <li>作用域下发（SOURCE / TASK）时的“全量匹配”占位 —— {@link #ALL}</li>
 *     <li>规则匹配条件中“未指定 / 不关心”占位 —— {@link #ANY}</li>
 *     <li>表达式渲染规则中布尔取反标记的规范化结果 —— {@link #NEGATED_TRUE} / {@link #NEGATED_FALSE}</li>
 * </ul>
 * 在持久化与快照装载过程中，通过 {@code RegistryKeyUtils.*} 方法实现统一归一化；
 * 常量取值保持极简（ALL/ANY/T/F）以降低索引 & 存储开销，同时配合清晰 Javadoc 保证可读性。
 * </p>
 *
 * <p>参考：docs/patra-registry/expr/Registry-expr-schema-design.md</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public final class RegistryKeys {
    private RegistryKeys() {}

    /** 任务 / 来源维度下的“全量”标记，表示不针对某个具体 task / source 的特化配置。 */
    public static final String ALL = "ALL";
    /** 匹配条件 / valueType / matchType 未指定或“任意”占位。 */
    public static final String ANY = "ANY";
    /** 规则取反：已显式设置为 true 的规范化 Key（尽量使用单字符提升组合 Key 压缩度）。 */
    public static final String NEGATED_TRUE = "T";
    /** 规则取反：为 false（或未取反）时的规范化 Key。 */
    public static final String NEGATED_FALSE = "F";
}
