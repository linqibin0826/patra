/// 错误处理管道包。
/// 
/// 本包实现基于责任链模式的错误解析管道,支持在错误解析过程中插入拦截器, 执行追踪传播、指标收集、熔断保护等横切关注点。
/// 
/// ## 职责
/// 
/// - 组织和执行拦截器责任链
///   - 按 `@Order` 排序拦截器
///   - 委托给错误解析引擎获取最终结果
///   - 提供拦截器的前置/后置处理能力
/// 
/// ## 核心组件
/// 
/// - {@link com.patra.starter.core.error.pipeline.ErrorResolutionPipeline} - 错误解析管道
///   - {@link com.patra.starter.core.error.pipeline.ResolutionInterceptor} - 拦截器接口
///   - {@link com.patra.starter.core.error.pipeline.ResolutionInvocation} - 调用链抽象
/// 
/// ## 执行流程
/// 
/// ```
/// 
/// 异常输入
///   ↓
/// ErrorResolutionPipeline.resolve(exception)
///   ↓
/// 拦截器链 (按 @Order 排序)
///   ├─ TracingInterceptor (@Order(10))     - 传播追踪上下文
///   ├─ MetricsInterceptor (@Order(20))     - 记录指标
///   ├─ CircuitBreakerInterceptor (@Order(30)) - 熔断保护(可选)
///   └─ CustomInterceptor (@Order(100))     - 自定义拦截器
///   ↓
/// ErrorResolutionEngine.resolve(exception)  - 核心解析逻辑
///   ↓
/// ErrorResolution (标准化错误表示)
/// 
/// ```
/// 
/// ## 拦截器排序
/// 
/// 使用 `@Order` 注解控制执行顺序,数值越小优先级越高:
/// 
/// - **10** - TracingInterceptor(追踪传播)
///   - **20** - MetricsInterceptor(指标收集)
///   - **30** - CircuitBreakerInterceptor(熔断保护)
///   - **100+** - 自定义拦截器
/// 
/// ## 使用示例
/// 
/// ### 直接使用管道
/// 
/// ```java
/// @RestControllerAdvice
/// public class GlobalExceptionHandler {
///     private final ErrorResolutionPipeline pipeline;
/// 
///     @ExceptionHandler(Exception.class)
///     public ResponseEntity<ProblemDetail> handleException(Exception ex) {
///         // 管道自动执行所有拦截器并调用引擎
///         ErrorResolution resolution = pipeline.resolve(ex);
/// 
///         ProblemDetail problem = ProblemDetail.forStatus(resolution.getHttpStatus());
///         problem.setDetail(resolution.getMessage());
/// 
///         return ResponseEntity.status(resolution.getHttpStatus()).body(problem);
/// ```
/// 
/// ### 自定义拦截器
/// 
/// ```java
/// @Component
/// @Order(100)
/// public class CustomLoggingInterceptor implements ResolutionInterceptor {
///     private static final Logger log = LoggerFactory.getLogger(CustomLoggingInterceptor.class);
/// 
///     @Override
///     public ErrorResolution intercept(Throwable exception, ResolutionInvocation invocation) {
///         // 前置处理
///         log.info("开始解析异常: {", exception.getClass().getSimpleName());
/// 
///         // 调用链下游(其他拦截器 + 引擎)
///         ErrorResolution resolution = invocation.proceed(exception);
/// 
///         // 后置处理
///         log.info("解析完成: errorCode={, httpStatus={",
///             resolution.getErrorCode(),
///             resolution.getHttpStatus()
///         );
/// 
///         return resolution;
/// ```
/// 
/// ### 条件性拦截器
/// 
/// ```java
/// @Component
/// @Order(50)
/// @ConditionalOnProperty(name = "patra.error.audit.enabled", havingValue = "true")
/// public class AuditInterceptor implements ResolutionInterceptor {
///     private final AuditService auditService;
/// 
///     @Override
///     public ErrorResolution intercept(Throwable exception, ResolutionInvocation invocation) {
///         ErrorResolution resolution = invocation.proceed(exception);
/// 
///         // 仅对业务异常记录审计日志
///         if (resolution.getHttpStatus() >= 400 && resolution.getHttpStatus() < 500) {
///             auditService.logBusinessError(resolution);
/// 
///         return resolution;
/// ```
/// 
/// ## 设计模式
/// 
/// - **责任链模式** - 拦截器链式调用
///   - **模板方法** - `ResolutionInvocation` 定义调用骨架
///   - **策略模式** - 不同拦截器实现不同策略
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.core.error.pipeline;
