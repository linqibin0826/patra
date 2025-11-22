/// Provenance HTTP 客户端包（已迁移到 Spring RestClient）。
/// 
/// 此包曾提供轻量级的 HTTP 客户端实现（SimpleHttpClient）。
/// 现已迁移到使用 Spring RestClient 进行 HTTP 调用。
/// 
/// HTTP 客户端配置现在通过 Spring Boot 自动配置完成：
/// 
/// - PubMed RestClient - 在 `ProvenanceAutoConfiguration.pubMedRestClient()` 中配置
///   - EPMC RestClient - 在 `ProvenanceAutoConfiguration.epmcRestClient()` 中配置
/// 
/// 超时和请求头配置从 `ProvenanceConfig.http()` 中提取。
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.provenance.common.http;
