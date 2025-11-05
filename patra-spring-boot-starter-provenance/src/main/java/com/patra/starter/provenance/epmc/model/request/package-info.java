/**
 * Europe PMC 请求模型包。
 *
 * <p>定义 Europe PMC API 的请求对象。
 *
 * <h2>请求类型</h2>
 *
 * <ul>
 *   <li>{@link SearchRequest} - 搜索请求
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * SearchRequest request = new SearchRequest();
 * request.setQuery("cancer AND open access:y");
 * request.setPageSize(100);
 * request.setFormat("json");
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.provenance.epmc.model.request;
