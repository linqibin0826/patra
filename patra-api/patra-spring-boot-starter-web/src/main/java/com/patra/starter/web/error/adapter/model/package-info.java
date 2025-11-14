/**
 * ProblemDetail 适配器模型包。
 *
 * <p>本包定义 ProblemDetail 适配器使用的数据传输对象(DTO)。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>封装 ProblemDetail 适配结果
 *   <li>关联 ProblemDetail、ErrorResolution 和 HTTP 状态
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link com.patra.starter.web.error.adapter.model.ProblemDetailResponse} - ProblemDetail
 *       响应封装
 * </ul>
 *
 * <h2>ProblemDetailResponse 结构</h2>
 *
 * <pre>{@code
 * public record ProblemDetailResponse(
 *     ProblemDetail problemDetail,       // RFC 7807 ProblemDetail 对象
 *     ErrorResolution errorResolution,   // 错误解析结果(包含错误码)
 *     HttpStatus httpStatus              // HTTP 状态码
 * ) {}
 * }</pre>
 *
 * <h2>使用示例</h2>
 *
 * <h3>创建 ProblemDetailResponse</h3>
 *
 * <pre>{@code
 * @Component
 * public class DefaultProblemDetailAdapter implements ProblemDetailAdapter {
 *     private final ErrorResolutionPipeline pipeline;
 *     private final ProblemDetailBuilder builder;
 *
 *     @Override
 *     public ProblemDetailResponse adapt(Throwable exception, HttpServletRequest request) {
 *         // 1. 解析异常
 *         ErrorResolution resolution = pipeline.resolve(exception);
 *
 *         // 2. 构建 ProblemDetail
 *         ProblemDetail problemDetail = builder.build(resolution, exception, request);
 *
 *         // 3. 封装为 ProblemDetailResponse
 *         return new ProblemDetailResponse(
 *             problemDetail,
 *             resolution,
 *             HttpStatus.valueOf(resolution.getHttpStatus())
 *         );
 *     }
 * }
 * }</pre>
 *
 * <h3>使用 ProblemDetailResponse</h3>
 *
 * <pre>{@code
 * @RestControllerAdvice
 * public class GlobalRestExceptionHandler {
 *     private final ProblemDetailAdapter adapter;
 *
 *     @ExceptionHandler(Exception.class)
 *     public ResponseEntity<ProblemDetail> handleException(
 *         Exception ex,
 *         HttpServletRequest request
 *     ) {
 *         ProblemDetailResponse response = adapter.adapt(ex, request);
 *
 *         // 访问封装的数据
 *         ProblemDetail problemDetail = response.problemDetail();
 *         HttpStatus httpStatus = response.httpStatus();
 *         String errorCode = response.errorResolution().errorCode().code();
 *
 *         // 记录日志
 *         log.error("Error code: {}, HTTP status: {}", errorCode, httpStatus.value());
 *
 *         // 返回响应
 *         return ResponseEntity
 *             .status(httpStatus)
 *             .contentType(MediaType.APPLICATION_PROBLEM_JSON)
 *             .body(problemDetail);
 *     }
 * }
 * }</pre>
 *
 * <h3>完整示例</h3>
 *
 * <pre>{@code
 * // 异常
 * Exception ex = new PlanNotFoundException(123L);
 *
 * // 适配结果
 * ProblemDetailResponse response = adapter.adapt(ex, request);
 *
 * // ProblemDetail (RFC 7807 标准对象)
 * response.problemDetail() = {
 *   "type": "https://api.patra.com/errors/plan-not-found",
 *   "title": "Not Found",
 *   "status": 404,
 *   "detail": "计划未找到: ID=123",
 *   "instance": "/api/plans/123",
 *   "traceId": "abc123",
 *   "timestamp": "2025-01-12T10:30:45.123Z"
 * }
 *
 * // ErrorResolution (内部错误表示)
 * response.errorResolution() = {
 *   "errorCode": "PLAN_NOT_FOUND",
 *   "httpStatus": 404,
 *   "message": "计划未找到: ID=123"
 * }
 *
 * // HttpStatus (Spring 枚举)
 * response.httpStatus() = HttpStatus.NOT_FOUND
 * }</pre>
 *
 * <h2>为什么需要 ProblemDetailResponse</h2>
 *
 * <ol>
 *   <li><strong>关联信息</strong> - 同时提供 ProblemDetail(面向客户端)和 ErrorResolution(面向服务端)
 *   <li><strong>类型安全</strong> - 使用强类型 HttpStatus 而非 int
 *   <li><strong>便于日志</strong> - 可直接访问 ErrorResolution 的错误码用于日志记录
 *   <li><strong>扩展性</strong> - 未来可添加更多元数据(如耗时、追踪信息等)
 * </ol>
 *
 * <h2>设计原则</h2>
 *
 * <ul>
 *   <li><strong>不可变性</strong> - 使用 Java 17 {@code record},线程安全
 *   <li><strong>封装性</strong> - 封装适配结果,避免直接暴露内部细节
 *   <li><strong>关注点分离</strong> - ProblemDetail 面向客户端,ErrorResolution 面向服务端
 * </ul>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.web.error.adapter.model;
