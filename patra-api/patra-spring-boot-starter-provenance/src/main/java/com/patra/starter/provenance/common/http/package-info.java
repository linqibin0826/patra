/**
 * Provenance HTTP 客户端包。
 *
 * <p>提供轻量级的 HTTP 客户端实现，支持弹性配置（超时、重试、限流）。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>执行 HTTP 请求（GET、POST）
 *   <li>应用弹性配置（连接超时、读取超时）
 *   <li>支持自定义请求头和查询参数
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link SimpleHttpClient} - 简单 HTTP 客户端
 *   <li>{@link HttpResilienceConfig} - HTTP 弹性配置
 * </ul>
 *
 * <h2>特性</h2>
 *
 * <ul>
 *   <li>基于 JDK HttpClient 实现
 *   <li>支持同步和异步调用
 *   <li>集成超时和重试机制
 *   <li>自动处理 JSON/XML 响应
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * SimpleHttpClient client = new SimpleHttpClient();
 * HttpResilienceConfig config = new HttpResilienceConfig(
 *     Duration.ofSeconds(5),  // connectTimeout
 *     Duration.ofSeconds(30)  // readTimeout
 * );
 *
 * String response = client.get(
 *     "https://api.example.com/search",
 *     Map.of("query", "cancer"),
 *     config
 * );
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.provenance.common.http;
