/**
 * 错误处理管道包。
 *
 * <p>本包实现基于责任链模式的错误解析管道,支持在错误解析过程中插入拦截器,
 * 执行追踪传播、指标收集、熔断保护等横切关注点。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>组织和执行拦截器责任链
 *   <li>按 {@code @Order} 排序拦截器
 *   <li>委托给错误解析引擎获取最终结果
 *   <li>提供拦截器的前置/后置处理能力
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link com.patra.starter.core.error.pipeline.ErrorResolutionPipeline} - 错误解析管道
 *   <li>{@link com.patra.starter.core.error.pipeline.ResolutionInterceptor} - 拦截器接口
 *   <li>{@link com.patra.starter.core.error.pipeline.ResolutionInvocation} - 调用链抽象
 * </ul>
 *
 * <h2>执行流程</h2>
 *
 * <pre>
 * 异常输入
 *   ↓
 * ErrorResolutionPipeline.resolve(exception)
 *   ↓
 * 拦截器链 (按 @Order 排序)
 *   ├─ TracingInterceptor (@Order(10))     - 传播追踪上下文
 *   ├─ MetricsInterceptor (@Order(20))     - 记录指标
 *   ├─ CircuitBreakerInterceptor (@Order(30)) - 熔断保护(可选)
 *   └─ CustomInterceptor (@Order(100))     - 自定义拦截器
 *   ↓
 * ErrorResolutionEngine.resolve(exception)  - 核心解析逻辑
 *   ↓
 * ErrorResolution (标准化错误表示)
 * </pre>
 *
 * <h2>拦截器排序</h2>
 *
 * <p>使用 {@code @Order} 注解控制执行顺序,数值越小优先级越高:
 *
 * <ul>
 *   <li><strong>10</strong> - TracingInterceptor(追踪传播)
 *   <li><strong>20</strong> - MetricsInterceptor(指标收集)
 *   <li><strong>30</strong> - CircuitBreakerInterceptor(熔断保护)
 *   <li><strong>100+</strong> - 自定义拦截器
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <h3>直接使用管道</h3>
 * <pre>{@code
 * @RestControllerAdvice
 * public class GlobalExceptionHandler {
 *     private final ErrorResolutionPipeline pipeline;
 *
 *     @ExceptionHandler(Exception.class)
 *     public ResponseEntity<ProblemDetail> handleException(Exception ex) {
 *         // 管道自动执行所有拦截器并调用引擎
 *         ErrorResolution resolution = pipeline.resolve(ex);
 *
 *         ProblemDetail problem = ProblemDetail.forStatus(resolution.getHttpStatus());
 *         problem.setDetail(resolution.getMessage());
 *
 *         return ResponseEntity.status(resolution.getHttpStatus()).body(problem);
 *     }
 * }
 * }</pre>
 *
 * <h3>自定义拦截器</h3>
 * <pre>{@code
 * @Component
 * @Order(100)
 * public class CustomLoggingInterceptor implements ResolutionInterceptor {
 *     private static final Logger log = LoggerFactory.getLogger(CustomLoggingInterceptor.class);
 *
 *     @Override
 *     public ErrorResolution intercept(Throwable exception, ResolutionInvocation invocation) {
 *         // 前置处理
 *         log.info("开始解析异常: {}", exception.getClass().getSimpleName());
 *
 *         // 调用链下游(其他拦截器 + 引擎)
 *         ErrorResolution resolution = invocation.proceed(exception);
 *
 *         // 后置处理
 *         log.info("解析完成: errorCode={}, httpStatus={}",
 *             resolution.getErrorCode(),
 *             resolution.getHttpStatus()
 *         );
 *
 *         return resolution;
 *     }
 * }
 * }</pre>
 *
 * <h3>条件性拦截器</h3>
 * <pre>{@code
 * @Component
 * @Order(50)
 * @ConditionalOnProperty(name = "patra.error.audit.enabled", havingValue = "true")
 * public class AuditInterceptor implements ResolutionInterceptor {
 *     private final AuditService auditService;
 *
 *     @Override
 *     public ErrorResolution intercept(Throwable exception, ResolutionInvocation invocation) {
 *         ErrorResolution resolution = invocation.proceed(exception);
 *
 *         // 仅对业务异常记录审计日志
 *         if (resolution.getHttpStatus() >= 400 && resolution.getHttpStatus() < 500) {
 *             auditService.logBusinessError(resolution);
 *         }
 *
 *         return resolution;
 *     }
 * }
 * }</pre>
 *
 * <h2>设计模式</h2>
 *
 * <ul>
 *   <li><strong>责任链模式</strong> - 拦截器链式调用
 *   <li><strong>模板方法</strong> - {@code ResolutionInvocation} 定义调用骨架
 *   <li><strong>策略模式</strong> - 不同拦截器实现不同策略
 * </ul>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.core.error.pipeline;
