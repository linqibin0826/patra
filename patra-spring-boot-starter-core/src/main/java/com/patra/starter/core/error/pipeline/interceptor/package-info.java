/// 错误处理管道内置拦截器包。
/// 
/// 本包提供错误处理管道的核心拦截器实现,涵盖追踪传播、指标收集和熔断保护等关键能力。 所有拦截器都实现 {@link
/// com.patra.starter.core.error.pipeline.ResolutionInterceptor} 接口。
/// 
/// ## 职责
/// 
/// - 提供追踪上下文传播能力(TracingInterceptor)
///   - 提供指标收集和慢解析检测(MetricsInterceptor)
///   - 提供熔断保护,防止错误解析雪崩(CircuitBreakerInterceptor)
/// 
/// ## 核心组件
/// 
/// - {@link com.patra.starter.core.error.pipeline.interceptor.TracingInterceptor} -
///       追踪传播拦截器(@Order(10))
///   - {@link com.patra.starter.core.error.pipeline.interceptor.MetricsInterceptor} -
///       指标收集拦截器(@Order(20))
///   - {@link com.patra.starter.core.error.pipeline.interceptor.CircuitBreakerInterceptor} -
///       熔断器拦截器(@Order(30),可选)
/// 
/// ## 拦截器详解
/// 
/// ### TracingInterceptor (@Order(10))
/// 
/// - **职责**: 提取和传播分布式追踪上下文
///   - **优先级**: 最高(10),确保追踪 ID 在所有拦截器中可用
///   - **依赖**: {@link com.patra.starter.core.error.spi.TraceProvider}
///   - **行为**: 从 HTTP Header/SkyWalking 提取追踪 ID,存入 MDC
/// 
/// ### MetricsInterceptor (@Order(20))
/// 
/// - **职责**: 记录错误解析指标和检测慢解析
///   - **优先级**: 中等(20),需要追踪 ID 已就绪
///   - **依赖**: {@link
///       com.patra.starter.core.error.observation.ErrorObservationRecorder}
///   - **行为**: 记录解析耗时、错误类型分布,超阈值记录警告日志
/// 
/// ### CircuitBreakerInterceptor (@Order(30),可选)
/// 
/// - **职责**: 保护错误解析管道,防止级联故障
///   - **优先级**: 较低(30),仅保护核心解析逻辑
///   - **依赖**: Resilience4j CircuitBreaker(可选依赖)
///   - **激活条件**: `resilience4j-circuitbreaker` 在 classpath 且 `patra.error.circuit-breaker.enabled=true`
///   - **行为**: 熔断器打开时返回降级错误,避免雪崩
/// 
/// ## 执行顺序
/// 
/// ```
/// 
/// 异常输入
///   ↓
/// TracingInterceptor (Order=10)
///   ├─ 提取追踪 ID
///   ├─ 存入 MDC
///   └─ 调用下游 ↓
/// 
/// MetricsInterceptor (Order=20)
///   ├─ 记录开始时间
///   ├─ 调用下游 ↓
///   └─ 记录指标和慢解析
/// 
/// CircuitBreakerInterceptor (Order=30, 可选)
///   ├─ 检查熔断器状态
///   ├─ 打开状态 → 返回降级错误
///   └─ 关闭/半开 → 调用下游 ↓
/// 
/// ErrorResolutionEngine
///   └─ 核心解析逻辑
/// 
/// ```
/// 
/// ## 使用示例
/// 
/// ### 默认配置(自动激活)
/// 
/// ```java
/// patra:
///   error:
///     enabled: true                          # TracingInterceptor 和 MetricsInterceptor 自动激活
///     observation:
///       enabled: true                        # 启用 MetricsInterceptor
///       slow-threshold-ms: 200
/// 
///   tracing:
///     header-names:
///       - X-Trace-ID                         # TracingInterceptor 提取的 Header
/// ```
/// 
/// ### 启用熔断器
/// 
/// ```java
/// patra:
///   error:
///     circuit-breaker:
///       enabled: true                        # 激活 CircuitBreakerInterceptor
///       failure-rate-threshold: 50.0
///       minimum-number-of-calls: 20
///       sliding-window-size: 50
/// ```
/// 
/// ### 查看拦截器执行日志
/// 
/// ```
/// 
/// 2025-01-12 10:30:45.123 [traceId=abc123] DEBUG TracingInterceptor - 提取追踪 ID: abc123
/// 2025-01-12 10:30:45.125 [traceId=abc123] DEBUG MetricsInterceptor - 错误解析开始: PlanNotFoundException
/// 2025-01-12 10:30:45.135 [traceId=abc123] WARN  MetricsInterceptor - 慢解析检测: 耗时 250ms, 异常类型: PlanNotFoundException
/// 2025-01-12 10:30:45.136 [traceId=abc123] INFO  CircuitBreakerInterceptor - 熔断器状态: CLOSED
/// 
/// ```
/// 
/// ## 扩展自定义拦截器
/// 
/// ```java
/// @Component
/// @Order(25)  // 插入 MetricsInterceptor 和 CircuitBreakerInterceptor 之间
/// public class CustomAuditInterceptor implements ResolutionInterceptor {
///     private final AuditService auditService;
/// 
///     @Override
///     public ErrorResolution intercept(Throwable exception, ResolutionInvocation invocation) {
///         ErrorResolution resolution = invocation.proceed(exception);
/// 
///         // 审计关键业务异常
///         if (resolution.getHttpStatus() >= 400) {
///             auditService.logError(resolution);
/// 
///         return resolution;
/// ```
/// 
/// ## 熔断器降级策略
/// 
/// 当熔断器打开时,返回默认降级错误:
/// 
/// ```java
/// {
///   "errorCode": "CIRCUIT_BREAKER_OPEN",
///   "message": "错误处理服务暂时不可用,请稍后重试",
///   "httpStatus": 503
/// ```
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.core.error.pipeline.interceptor;
