/**
 * Provenance 网关请求构建包。
 *
 * <p>提供构建和发送网关请求的工具类。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>构建标准化的 API 请求对象
 *   <li>封装请求参数和请求头
 *   <li>支持请求构建的流式 API
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link ApiRequest} - API 请求模型
 *   <li>{@link GatewayRequestBuilder} - 网关请求构建器
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * ApiRequest request = GatewayRequestBuilder.builder()
 *     .url("https://api.example.com/search")
 *     .header("Content-Type", "application/json")
 *     .queryParam("query", "cancer")
 *     .queryParam("pageSize", "100")
 *     .build();
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.provenance.common.gateway;
