/**
 * Provenance HTTP 客户端包（已迁移到 Spring RestClient）。
 *
 * <p>此包曾提供轻量级的 HTTP 客户端实现（SimpleHttpClient）。
 * 现已迁移到使用 Spring RestClient 进行 HTTP 调用。
 *
 * <p>HTTP 客户端配置现在通过 Spring Boot 自动配置完成：
 *
 * <ul>
 *   <li>PubMed RestClient - 在 {@code ProvenanceAutoConfiguration.pubMedRestClient()} 中配置
 *   <li>EPMC RestClient - 在 {@code ProvenanceAutoConfiguration.epmcRestClient()} 中配置
 * </ul>
 *
 * <p>超时和请求头配置从 {@code ProvenanceConfig.http()} 中提取。
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.provenance.common.http;
