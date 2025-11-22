/// Feign 错误处理拦截器包。
///
/// 提供请求拦截器，用于传播分布式跟踪标识符和其他上下文信息。
///
/// ## 职责
///
/// - 在 Feign 请求中传播 TraceId
///   - 支持多种 TraceId 格式（MDC、ThreadLocal、Header）
///   - 记录 TraceId 传播指标
///
/// ## 核心组件
///
/// - {@link TraceIdRequestInterceptor} - TraceId 请求拦截器
///
/// ## TraceId 来源优先级
///
/// ## 传播 Header
///
/// - `traceId` - 自定义 Header（默认）
///   - `X-B3-TraceId` - Zipkin B3 格式（可选）
///   - `traceparent` - W3C Trace Context（可选）
///
/// ## 使用示例
///
/// ```java
/// // 在业务代码中设置 TraceId
/// MDC.put("traceId", UUID.randomUUID().toString());
/// try {
///     ProvenanceResponse response = registryClient.getProvenance(code);
///     // TraceId 会自动传播到下游服务 finally {
///     MDC.remove("traceId");
/// ```
///
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.feign.error.interceptor;
