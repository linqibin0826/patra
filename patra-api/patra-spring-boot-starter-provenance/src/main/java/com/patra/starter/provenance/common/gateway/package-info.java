/// Provenance 网关请求构建包。
///
/// 提供构建和发送网关请求的工具类。
///
/// ## 职责
///
/// - 构建标准化的 API 请求对象
///   - 封装请求参数和请求头
///   - 支持请求构建的流式 API
///
/// ## 核心组件
///
/// - {@link ApiRequest} - API 请求模型
///   - {@link GatewayRequestBuilder} - 网关请求构建器
///
/// ## 使用示例
///
/// ```java
/// ApiRequest request = GatewayRequestBuilder.builder()
///     .url("https://api.example.com/search")
///     .header("Content-Type", "application/json")
///     .queryParam("query", "cancer")
///     .queryParam("pageSize", "100")
///     .build();
/// ```
///
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.provenance.common.gateway;
