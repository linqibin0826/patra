/**
 * Provenance API 参数键名常量
 *
 * <p>定义各数据源的 HTTP 查询参数键名，作为单一事实来源（SSOT）。
 *
 * <h3>使用示例</h3>
 *
 * <pre>{@code
 * // 构建查询参数
 * Map<String, String> params = new HashMap<>();
 * params.put(PubMedParamKeys.TERM, "cancer");
 * params.put(PubMedParamKeys.RETMODE, RetMode.JSON.value());
 * params.put(PubMedParamKeys.RETMAX, "100");
 * }</pre>
 *
 * @author linqibin
 * @since 0.1.0
 */
package com.patra.common.provenance.api.params;
