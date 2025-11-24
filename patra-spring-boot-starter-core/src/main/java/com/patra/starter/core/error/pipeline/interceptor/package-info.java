/// 错误处理管道内置拦截器包。
///
/// 本包提供错误处理管道的核心拦截器实现,专注于弹性保护能力。
/// 所有拦截器都实现 {@link com.patra.starter.core.error.pipeline.ResolutionInterceptor} 接口。
///
/// ## 职责
///
/// - 提供熔断保护,防止错误解析雪崩(CircuitBreakerInterceptor)
///
/// 注意: 追踪传播和指标收集功能已移至 patra-spring-boot-starter-observability,
/// 通过 ResolutionInterceptor 扩展点机制提供,符合关注点分离原则。
///
/// ## 核心组件
///
/// - {@link com.patra.starter.core.error.pipeline.interceptor.CircuitBreakerInterceptor} -
///       熔断器拦截器(可选,需要 resilience4j-circuitbreaker)
///
/// ## 拦截器详解
///
/// ### CircuitBreakerInterceptor (可选)
///
/// - **职责**: 保护错误解析管道,防止级联故障
///   - **优先级**: HIGHEST_PRECEDENCE + 10,保护核心解析逻辑
///   - **依赖**: Resilience4j CircuitBreaker(可选依赖)
///   - **激活条件**: `resilience4j-circuitbreaker` 在 classpath 且 `patra.error.circuit-breaker.enabled=true`
///   - **行为**: 熔断器打开时返回降级错误(503),避免雪崩
///
/// ## 执行顺序
///
/// ```
///
/// 异常输入
///   ↓
/// (可选) ObservabilityInterceptor - 由 patra-spring-boot-starter-observability 提供
///   ├─ 追踪上下文传播
///   ├─ 指标记录
///   └─ 调用下游 ↓
///
/// CircuitBreakerInterceptor (可选)
///   ├─ 检查熔断器状态
///   ├─ 打开状态 → 返回降级错误(503)
///   └─ 关闭/半开 → 调用下游 ↓
///
/// ErrorResolutionEngine
///   └─ 核心解析逻辑
///
/// ```
///
/// ## 使用示例
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
/// ### 查看熔断器日志
///
/// ```
///
/// 2025-01-12 10:30:45.136 INFO  CircuitBreakerInterceptor - 创建错误解析熔断器: 失败率阈值=50.0 滑动窗口大小=50
/// 2025-01-12 10:35:10.500 WARN  CircuitBreakerInterceptor - 错误解析期间熔断器已打开,使用降级错误码
///
/// ```
///
/// ## 扩展自定义拦截器
///
/// 通过实现 ResolutionInterceptor 接口添加自定义拦截器:
///
/// ```java
/// @Component
/// @Order(100)  // 自定义优先级
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
///         }
///
///         return resolution;
///     }
/// }
/// ```
///
/// ## 熔断器降级策略
///
/// 当熔断器打开时,返回默认降级错误:
///
/// ```java
/// {
///   "errorCode": "{contextPrefix}:0503",
///   "httpStatus": 503
/// }
/// ```
///
/// ## 可观测性支持
///
/// 追踪传播和指标收集由 patra-spring-boot-starter-observability 提供:
/// - 添加 patra-spring-boot-starter-observability 依赖自动启用
/// - 通过 ResolutionInterceptor 扩展点无缝集成
/// - 符合依赖倒置原则(DIP)和关注点分离(SoC)原则
///
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.core.error.pipeline.interceptor;
