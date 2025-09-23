package com.patra.registry.api.rpc.dto.provenance;

/**
 * 数据来源（Provenance）基础元数据响应 DTO。<br>
 * <p>承载 reg_provenance 主表中对外必要的只读字段，用于：
 * <ul>
 *   <li>标识来源唯一性（code）供下游缓存 / 任务编排引用；</li>
 *   <li>为端点 / HTTP / 凭证等子配置提供默认基线（如基础 URL、默认时区）。</li>
 * </ul>
 * </p>
 * 字段含义：
 * <ul>
 *   <li>{@code id} 内部数据库主键（数值发号）；不推荐跨环境持久引用，只做内部连接。</li>
 *   <li>{@code code} 业务稳定编码（如 PUBMED），跨环境唯一，对所有 reg_prov_* 表的 provenance_id 逻辑主锚。</li>
 *   <li>{@code name} 可读显示名称（UI/日志展示）。</li>
 *   <li>{@code baseUrlDefault} 默认基础请求根地址（若某端点未单独覆盖）。</li>
 *   <li>{@code timezoneDefault} 默认业务解释时区（窗口/日期字段解析用；未配置 windowOffset 时亦可参考）。</li>
 *   <li>{@code docsUrl} 官方文档或能力说明 URL（供调试/审计跳转）。</li>
 *   <li>{@code active} 是否激活（false 时可短路调度）。</li>
 *   <li>{@code lifecycleStatusCode} 生命周期状态（如 PLANNING / ACTIVE / DEPRECATED / RETIRED）。</li>
 * </ul>
 * @author linqibin
 * @since 0.1.0
 */
public record ProvenanceResp(
        /** 内部主键 ID */
        Long id,
        /** 稳定来源编码（大写/蛇形） */
        String code,
        /** 显示名称 */
        String name,
        /** 默认基础 URL（可被 http 配置覆盖） */
        String baseUrlDefault,
        /** 默认业务时区（窗口/日期解析） */
        String timezoneDefault,
        /** 官方/文档链接 */
        String docsUrl,
        /** 是否激活 */
        boolean active,
        /** 生命周期状态编码 */
        String lifecycleStatusCode
) {
}
