/// ProblemDetail 适配器模型包。
///
/// 本包定义 ProblemDetail 适配器使用的数据传输对象(DTO)。
///
/// ## 职责
///
/// - 封装 ProblemDetail 适配结果
///   - 关联 ProblemDetail、ErrorResolution 和 HTTP 状态
///
/// ## 核心组件
///
/// - {@link com.patra.starter.web.error.adapter.model.ProblemDetailResponse} - ProblemDetail
///       响应封装
///
/// ## ProblemDetailResponse 结构
///
/// ```java
/// public record ProblemDetailResponse(
///     ProblemDetail problemDetail,       // RFC 7807 ProblemDetail 对象
///     ErrorResolution errorResolution,   // 错误解析结果(包含错误码)
///     HttpStatus httpStatus              // HTTP 状态码
/// ) {
/// ```
///
/// ## 使用示例
///
/// ### 创建 ProblemDetailResponse
///
/// ```java
/// @Component
/// public class DefaultProblemDetailAdapter implements ProblemDetailAdapter {
///     private final ErrorResolutionPipeline pipeline;
///     private final ProblemDetailBuilder builder;
///
///     @Override
///     public ProblemDetailResponse adapt(Throwable exception, HttpServletRequest request) {
///         // 1. 解析异常
///         ErrorResolution resolution = pipeline.resolve(exception);
///
///         // 2. 构建 ProblemDetail
///         ProblemDetail problemDetail = builder.build(resolution, exception, request);
///
///         // 3. 封装为 ProblemDetailResponse
///         return new ProblemDetailResponse(
///             problemDetail,
///             resolution,
///             HttpStatus.valueOf(resolution.getHttpStatus())
///         );
/// ```
///
/// ### 使用 ProblemDetailResponse
///
/// ```java
/// @RestControllerAdvice
/// public class GlobalRestExceptionHandler {
///     private final ProblemDetailAdapter adapter;
///
///     @ExceptionHandler(Exception.class)
///     public ResponseEntity<ProblemDetail> handleException(
///         Exception ex,
///         HttpServletRequest request
///     ) {
///         ProblemDetailResponse response = adapter.adapt(ex, request);
///
///         // 访问封装的数据
///         ProblemDetail problemDetail = response.problemDetail();
///         HttpStatus httpStatus = response.httpStatus();
///         String errorCode = response.errorResolution().errorCode().code();
///
///         // 记录日志
///         log.error("Error code: {, HTTP status: {", errorCode, httpStatus.value());
///
///         // 返回响应
///         return ResponseEntity
///             .status(httpStatus)
///             .contentType(MediaType.APPLICATION_PROBLEM_JSON)
///             .body(problemDetail);
/// ```
///
/// ### 完整示例
///
/// ```java
/// // 异常
/// Exception ex = new PlanNotFoundException(123L);
///
/// // 适配结果
/// ProblemDetailResponse response = adapter.adapt(ex, request);
///
/// // ProblemDetail (RFC 7807 标准对象)
/// response.problemDetail() = {
///   "type": "https://api.patra.com/errors/plan-not-found",
///   "title": "Not Found",
///   "status": 404,
///   "detail": "计划未找到: ID=123",
///   "instance": "/api/plans/123",
///   "traceId": "abc123",
///   "timestamp": "2025-01-12T10:30:45.123Z"
///
/// // ErrorResolution (内部错误表示)
/// response.errorResolution() = {
///   "errorCode": "PLAN_NOT_FOUND",
///   "httpStatus": 404,
///   "message": "计划未找到: ID=123"
///
/// // HttpStatus (Spring 枚举)
/// response.httpStatus() = HttpStatus.NOT_FOUND
/// ```
///
/// ## 为什么需要 ProblemDetailResponse
///
/// ## 设计原则
///
/// - **不可变性** - 使用 Java 17 `record`,线程安全
///   - **封装性** - 封装适配结果,避免直接暴露内部细节
///   - **关注点分离** - ProblemDetail 面向客户端,ErrorResolution 面向服务端
///
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.web.error.adapter.model;
