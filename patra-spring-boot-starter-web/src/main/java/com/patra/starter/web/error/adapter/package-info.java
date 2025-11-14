/**
 * ProblemDetail 适配器包。
 *
 * <p>本包提供异常到 RFC 7807 ProblemDetail 的适配能力,桥接核心错误处理框架和 Web 层。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>将异常转换为 RFC 7807 ProblemDetail
 *   <li>桥接 {@code ErrorResolutionPipeline} 和 Web 响应
 *   <li>集成 {@code ProblemDetailBuilder} 构建响应体
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link com.patra.starter.web.error.adapter.ProblemDetailAdapter} - 适配器接口
 *   <li>{@link com.patra.starter.web.error.adapter.DefaultProblemDetailAdapter} - 默认适配器实现
 * </ul>
 *
 * <h2>适配流程</h2>
 *
 * <pre>
 * Exception
 *   ↓
 * ProblemDetailAdapter.adapt(exception, request)
 *   ↓
 * 1. ErrorResolutionPipeline.resolve(exception)
 *    └─ 返回 ErrorResolution(errorCode, httpStatus, message)
 *   ↓
 * 2. ProblemDetailBuilder.build(resolution, exception, request)
 *    ├─ 创建 ProblemDetail.forStatus(httpStatus)
 *    ├─ 设置 type URI
 *    ├─ 设置 detail 消息
 *    ├─ 添加扩展字段(traceId, timestamp, path, etc)
 *    └─ 应用 ProblemFieldContributor
 *   ↓
 * ProblemDetailResponse(problemDetail, errorResolution, httpStatus)
 * </pre>
 *
 * <h2>使用示例</h2>
 *
 * <h3>在异常处理器中使用</h3>
 *
 * <pre>{@code
 * @RestControllerAdvice
 * public class GlobalRestExceptionHandler {
 *     private final ProblemDetailAdapter problemDetailAdapter;
 *
 *     @ExceptionHandler(Exception.class)
 *     public ResponseEntity<ProblemDetail> handleException(
 *         Exception ex,
 *         HttpServletRequest request
 *     ) {
 *         // 使用适配器转换异常
 *         ProblemDetailResponse response = problemDetailAdapter.adapt(ex, request);
 *
 *         return ResponseEntity
 *             .status(response.httpStatus())
 *             .contentType(MediaType.APPLICATION_PROBLEM_JSON)
 *             .body(response.problemDetail());
 *     }
 * }
 * }</pre>
 *
 * <h3>适配结果示例</h3>
 *
 * <pre>{@code
 * // 输入: PlanNotFoundException
 * Exception ex = new PlanNotFoundException(123L);
 *
 * // 输出: ProblemDetailResponse
 * {
 *   "problemDetail": {
 *     "type": "https://api.patra.com/errors/plan-not-found",
 *     "title": "Not Found",
 *     "status": 404,
 *     "detail": "计划未找到: ID=123",
 *     "instance": "/api/plans/123",
 *     "traceId": "abc123def456",
 *     "timestamp": "2025-01-12T10:30:45.123Z",
 *     "path": "/api/plans/123"
 *   },
 *   "errorResolution": {
 *     "errorCode": "PLAN_NOT_FOUND",
 *     "httpStatus": 404,
 *     "message": "计划未找到: ID=123"
 *   },
 *   "httpStatus": 404
 * }
 * }</pre>
 *
 * <h2>扩展点集成</h2>
 *
 * <p>ProblemDetailAdapter 自动集成所有扩展点:
 *
 * <ul>
 *   <li><strong>ErrorMappingContributor</strong> - 通过 ErrorResolutionPipeline 自动应用
 *   <li><strong>ProblemFieldContributor</strong> - 通过 ProblemDetailBuilder 自动应用
 *   <li><strong>WebProblemFieldContributor</strong> - Web 特定扩展字段
 * </ul>
 *
 * <h2>自定义适配器</h2>
 *
 * <pre>{@code
 * @Component
 * @Primary
 * public class CustomProblemDetailAdapter implements ProblemDetailAdapter {
 *     private final ErrorResolutionPipeline pipeline;
 *     private final ProblemDetailBuilder builder;
 *
 *     @Override
 *     public ProblemDetailResponse adapt(Throwable exception, HttpServletRequest request) {
 *         // 自定义解析逻辑
 *         ErrorResolution resolution = pipeline.resolve(exception);
 *
 *         // 自定义构建逻辑
 *         ProblemDetail problemDetail = builder.build(resolution, exception, request);
 *
 *         // 添加自定义字段
 *         problemDetail.setProperty("customField", "customValue");
 *
 *         return new ProblemDetailResponse(
 *             problemDetail,
 *             resolution,
 *             HttpStatus.valueOf(resolution.getHttpStatus())
 *         );
 *     }
 * }
 * }</pre>
 *
 * <h2>线程安全性</h2>
 *
 * <p>DefaultProblemDetailAdapter 是线程安全的,可以在并发环境下安全使用:
 *
 * <ul>
 *   <li>不维护可变状态
 *   <li>所有依赖组件(Pipeline、Builder)都是线程安全的
 *   <li>每次调用 {@code adapt()} 创建新的 ProblemDetailResponse
 * </ul>
 *
 * <h2>性能考虑</h2>
 *
 * <ul>
 *   <li>适配过程涉及错误解析管道(可能包含多个拦截器)
 *   <li>建议启用 {@code patra.error.observation.enabled} 监控解析耗时
 *   <li>慢解析会记录警告日志,便于性能调优
 * </ul>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.web.error.adapter;
