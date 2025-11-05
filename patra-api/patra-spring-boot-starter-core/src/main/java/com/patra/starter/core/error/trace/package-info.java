/**
 * 分布式追踪支持包。
 *
 * <p>本包提供分布式追踪上下文的提取和传播能力,支持从 HTTP Header、MDC、SkyWalking 等来源获取追踪 ID。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>从 HTTP 请求头提取追踪 ID
 *   <li>支持多种追踪 ID Header 格式(X-Trace-ID、X-B3-TraceId 等)
 *   <li>实现 {@link com.patra.starter.core.error.spi.TraceProvider} SPI
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link com.patra.starter.core.error.trace.HeaderBasedTraceProvider} - 基于 HTTP Header 的追踪 ID 提取器
 * </ul>
 *
 * <h2>支持的 Header 格式</h2>
 *
 * <p>默认支持以下追踪 ID Header(按优先级):
 *
 * <ul>
 *   <li><strong>X-Trace-ID</strong> - 自定义追踪 ID
 *   <li><strong>X-B3-TraceId</strong> - Zipkin/Brave B3 格式
 *   <li><strong>traceparent</strong> - W3C Trace Context 标准
 * </ul>
 *
 * <p>可通过配置自定义 Header 列表:
 *
 * <pre>{@code
 * patra:
 *   tracing:
 *     header-names:
 *       - X-Custom-Trace-ID
 *       - X-Request-ID
 *       - X-Correlation-ID
 * }</pre>
 *
 * <h2>使用示例</h2>
 *
 * <h3>自动提取追踪 ID</h3>
 * <pre>{@code
 * // TracingInterceptor 自动使用 HeaderBasedTraceProvider
 * @Component
 * @Order(10)
 * public class TracingInterceptor implements ResolutionInterceptor {
 *     private final TraceProvider traceProvider;
 *
 *     @Override
 *     public ErrorResolution intercept(Throwable exception, ResolutionInvocation invocation) {
 *         // 从 HTTP Header 提取追踪 ID
 *         Optional<String> traceId = traceProvider.getCurrentTraceId();
 *
 *         traceId.ifPresent(id -> MDC.put("traceId", id));
 *
 *         try {
 *             return invocation.proceed(exception);
 *         } finally {
 *             MDC.remove("traceId");
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h3>手动获取追踪 ID</h3>
 * <pre>{@code
 * @Service
 * public class MyService {
 *     private final TraceProvider traceProvider;
 *
 *     public void doSomething() {
 *         String traceId = traceProvider.getCurrentTraceId()
 *             .orElse("UNKNOWN");
 *
 *         log.info("[{}] 执行业务逻辑", traceId);
 *     }
 * }
 * }</pre>
 *
 * <h2>追踪 ID 流转</h2>
 *
 * <pre>
 * 客户端请求
 *   ↓
 * HTTP Header: X-Trace-ID = abc123
 *   ↓
 * Spring MVC Filter/Interceptor
 *   ↓
 * HeaderBasedTraceProvider.getCurrentTraceId()
 *   ↓
 * TracingInterceptor → MDC.put("traceId", "abc123")
 *   ↓
 * 日志输出: [abc123] 错误解析开始
 *   ↓
 * ProblemDetail: { "traceId": "abc123", ... }
 * </pre>
 *
 * <h2>集成 SkyWalking</h2>
 *
 * <p>当使用 SkyWalking APM 时,可以自定义 TraceProvider 从 SkyWalking 提取:
 *
 * <pre>{@code
 * @Component
 * @Order(5)  // 优先级高于 HeaderBasedTraceProvider
 * public class SkyWalkingTraceProvider implements TraceProvider {
 *     @Override
 *     public Optional<String> getCurrentTraceId() {
 *         String traceId = TraceContext.traceId();
 *         return Optional.ofNullable(traceId).filter(id -> !id.isEmpty());
 *     }
 * }
 * }</pre>
 *
 * <h2>多 Provider 优先级</h2>
 *
 * <p>支持同时注册多个 {@code TraceProvider},按 {@code @Order} 优先级调用:
 *
 * <pre>
 * TraceProvider 链
 *   ├─ SkyWalkingTraceProvider (@Order(5))       - 优先从 SkyWalking 提取
 *   ├─ HeaderBasedTraceProvider (@Order(10))     - 降级从 HTTP Header 提取
 *   └─ MDCTraceProvider (@Order(20))             - 最后从 MDC 提取
 *
 * 第一个返回非空结果的 Provider 生效
 * </pre>
 *
 * <h2>配置选项</h2>
 *
 * <pre>{@code
 * patra:
 *   tracing:
 *     header-names:                              # 追踪 ID Header 列表(按优先级)
 *       - X-Trace-ID
 *       - X-B3-TraceId
 *       - traceparent
 * }</pre>
 *
 * <h2>日志格式集成</h2>
 *
 * <p>配合 Logback {@code TraceIdConverter} 在日志中显示追踪 ID:
 *
 * <pre>{@code
 * <configuration>
 *   <conversionRule conversionWord="traceId"
 *                   converterClass="com.patra.starter.core.logging.TraceIdConverter"/>
 *
 *   <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
 *     <encoder>
 *       <pattern>%d{HH:mm:ss.SSS} [%traceId] %-5level %logger{36} - %msg%n</pattern>
 *     </encoder>
 *   </appender>
 * </configuration>
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.core.error.trace;
