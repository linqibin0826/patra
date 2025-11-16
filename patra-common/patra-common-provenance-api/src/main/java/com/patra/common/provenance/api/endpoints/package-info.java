/**
 * Provenance API 端点路径常量
 *
 * <p>定义各数据源的 API 端点路径，作为单一事实来源（SSOT）。
 *
 * <h3>使用示例</h3>
 *
 * <pre>{@code
 * // 构建完整 URL
 * String url = baseUrl + PubMedEndpoints.ESEARCH;
 *
 * // 在 RestClient 中使用
 * restClient.get()
 *     .uri(uriBuilder -> uriBuilder.path(PubMedEndpoints.ESEARCH).build())
 *     .retrieve();
 * }</pre>
 *
 * @author linqibin
 * @since 0.1.0
 */
package com.patra.common.provenance.api.endpoints;
