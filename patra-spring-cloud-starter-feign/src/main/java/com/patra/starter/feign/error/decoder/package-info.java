/**
 * Feign 错误解码器包。
 *
 * <p>实现基于 RFC 7807 ProblemDetail 的 Feign ErrorDecoder，支持智能解析和优雅降级。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>解析 {@code application/problem+json} 格式的错误响应
 *   <li>提取业务错误代码、跟踪标识符和扩展属性
 *   <li>在宽容模式下优雅处理非标准响应
 *   <li>记录解析性能和指标
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link ProblemDetailErrorDecoder} - ProblemDetail 错误解码器
 * </ul>
 *
 * <h2>解码策略</h2>
 *
 * <pre>
 * 1. 检查 Content-Type
 *    ├── application/problem+json
 *    │   ├── 解析 ProblemDetail
 *    │   │   ├── 提取 status、detail、title
 *    │   │   ├── 提取 extensions (errorCode, traceId, ...)
 *    │   │   └── 构造 RemoteCallException(problemDetail)
 *    │   └── 解析失败 → 进入宽容模式
 *    └── 其他 Content-Type → 进入宽容模式
 *
 * 2. 宽容模式（tolerant=true）
 *    ├── 读取响应体（最多 max-error-body-size 字节）
 *    ├── 尝试从 Header 提取 TraceId
 *    ├── 构造 RemoteCallException(status, message, traceId)
 *    └── 记录降级指标
 *
 * 3. 严格模式（tolerant=false）
 *    └── 抛出 FeignException.errorStatus()
 * </pre>
 *
 * <h2>TraceId 提取优先级</h2>
 *
 * <ol>
 *   <li>{@code traceId} - 自定义 Header
 *   <li>{@code X-B3-TraceId} - Zipkin B3 格式
 *   <li>{@code traceparent} - W3C Trace Context
 *   <li>{@code X-Trace-Id} - 通用格式
 * </ol>
 *
 * <h2>性能优化</h2>
 *
 * <ul>
 *   <li>响应体读取限制 {@code max-error-body-size}，防止 OOM
 *   <li>记录解析耗时，识别慢操作
 *   <li>缓存 Content-Type 解析结果
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * // 自定义 ErrorDecoder（如需）
 * @Bean
 * @ConditionalOnMissingBean
 * public ErrorDecoder feignErrorDecoder(
 *     ObjectMapper objectMapper,
 *     FeignErrorProperties properties,
 *     FeignErrorObservationRecorder recorder) {
 *     return new ProblemDetailErrorDecoder(objectMapper, properties, recorder);
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.feign.error.decoder;
