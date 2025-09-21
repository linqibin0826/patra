package com.patra.common.enums;

import java.util.Locale;

/**
 * Registry 配置生效维度（Configuration Distribution Scope）。
 * <p>
 * 该枚举用于标识一条 Registry 配置（包括：表达式字段能力 / 渲染规则 / API 参数映射、以及 Provenance 端点/分页/HTTP/重试/限流等来源配置）
 * 的适用范围层级：
 * <ul>
 *     <li>{@link #SOURCE}：来源级 —— 对某一数据来源 (Provenance) 下所有任务统一生效；</li>
 *     <li>{@link #TASK}：任务级 —— 仅对指定任务类型生效，可覆盖同一 Provenance 下的来源级配置。</li>
 * </ul>
 * </p>
 * <p>
 * 设计目标：让“这是哪一类配置的 Scope”在代码层面一眼可见，避免原先通用名 {@code RegistryScope} 与
 * 未来其它可能出现的 scope 字段（如授权 Scope、统计 Scope 等）混淆。
 * </p>
 * <p>
 * 持久化列仍使用字符串值 "SOURCE" / "TASK"，与历史兼容；DB 中旧数据无需迁移。
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 * @see #fromCode(String)
 */
public enum RegistryConfigScope {
    /** 来源级（provenance-level）。 */
    SOURCE,
    /** 任务级（task-type-level）。 */
    TASK;

    /** 返回用于持久化/查询的标准大写编码。 */
    public String code() {
        return name();
    }

    /** 解析（忽略大小写与前后空白），非法值抛出 {@link IllegalArgumentException}。 */
    public static RegistryConfigScope fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Scope code cannot be blank");
        }
        try {
            return RegistryConfigScope.valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown scope code: " + code, ex);
        }
    }
}

