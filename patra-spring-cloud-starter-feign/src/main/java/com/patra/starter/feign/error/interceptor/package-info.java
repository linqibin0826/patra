/**
 * Feign 错误处理拦截器包。
 *
 * <p>提供请求拦截器，用于传播分布式跟踪标识符和其他上下文信息。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>在 Feign 请求中传播 TraceId
 *   <li>支持多种 TraceId 格式（MDC、ThreadLocal、Header）
 *   <li>记录 TraceId 传播指标
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link TraceIdRequestInterceptor} - TraceId 请求拦截器
 * </ul>
 *
 * <h2>TraceId 来源优先级</h2>
 *
 * <ol>
 *   <li><b>SLF4J MDC</b> - {@code MDC.get("traceId")}
 *   <li><b>ThreadLocal</b> - 自定义 ThreadLocal 绑定
 *   <li><b>自动生成</b> - UUID（如果配置启用）
 * </ol>
 *
 * <h2>传播 Header</h2>
 *
 * <ul>
 *   <li>{@code traceId} - 自定义 Header（默认）
 *   <li>{@code X-B3-TraceId} - Zipkin B3 格式（可选）
 *   <li>{@code traceparent} - W3C Trace Context（可选）
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * // 在业务代码中设置 TraceId
 * MDC.put("traceId", UUID.randomUUID().toString());
 * try {
 *     ProvenanceResponse response = registryClient.getProvenance(code);
 *     // TraceId 会自动传播到下游服务
 * } finally {
 *     MDC.remove("traceId");
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.feign.error.interceptor;
