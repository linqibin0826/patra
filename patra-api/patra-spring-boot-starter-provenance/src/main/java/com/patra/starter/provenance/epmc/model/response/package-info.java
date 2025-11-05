/**
 * Europe PMC 响应模型包。
 *
 * <p>定义 Europe PMC API 的响应对象，映射 JSON 响应结构。
 *
 * <h2>响应类型</h2>
 *
 * <ul>
 *   <li>{@link SearchResponse} - 搜索响应
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * SearchResponse response = client.search(request);
 * int totalResults = response.getHitCount();
 * List<Result> results = response.getResultList();
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.provenance.epmc.model.response;
