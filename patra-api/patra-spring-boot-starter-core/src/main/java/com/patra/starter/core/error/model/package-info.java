/// 错误处理模型包。
///
/// 本包定义错误处理框架使用的核心领域模型,提供统一的错误表示和错误码抽象。
///
/// ## 职责
///
/// - 定义标准化的错误解析结果模型
///   - 提供简单错误码实现
///   - 封装错误码、HTTP 状态码、错误消息等信息
///
/// ## 核心组件
///
/// - {@link com.patra.starter.core.error.model.ErrorResolution} - 错误解析结果,包含错误码和 HTTP 状态
///   - {@link com.patra.starter.core.error.model.SimpleErrorCode} - 简单错误码实现
///
/// ## ErrorResolution 模型
///
/// `ErrorResolution` 是错误解析引擎的输出结果,包含以下信息:
///
/// - **错误码** - 机器可读的唯一标识符(如 `PLAN_NOT_FOUND`)
///   - **HTTP 状态码** - 对应的 HTTP 响应码(如 404)
///   - **错误消息** - 人类可读的错误描述
///   - **原始异常** - 引发错误的原始 `Throwable`
///
/// ## 使用示例
///
/// ### 创建错误解析结果
///
/// ```java
/// // 通过引擎自动解析
/// ErrorResolution resolution = errorResolutionEngine.resolve(exception);
///
/// // 手动构建
/// ErrorResolution resolution = ErrorResolution.builder()
///     .errorCode(new SimpleErrorCode("PLAN_NOT_FOUND", "计划未找到", 404))
///     .httpStatus(404)
///     .message("未找到 ID 为 123 的计划")
///     .exception(exception)
///     .build();
/// ```
///
/// ### 使用错误解析结果
///
/// ```java
/// @RestControllerAdvice
/// public class GlobalExceptionHandler {
///     private final ErrorResolutionPipeline pipeline;
///
///     @ExceptionHandler(Exception.class)
///     public ResponseEntity<ProblemDetail> handleException(Exception ex) {
///         ErrorResolution resolution = pipeline.resolve(ex);
///
///         ProblemDetail problem = ProblemDetail.forStatus(resolution.getHttpStatus());
///         problem.setDetail(resolution.getMessage());
///         problem.setProperty("errorCode", resolution.getErrorCode().getCode());
///
///         return ResponseEntity
///             .status(resolution.getHttpStatus())
///             .body(problem);
/// ```
///
/// ### 创建简单错误码
///
/// ```java
/// // 在 ErrorMappingContributor 中使用
/// @Component
/// public class MyErrorMappingContributor implements ErrorMappingContributor {
///     @Override
///     public Optional<ErrorCodeLike> mapException(Throwable exception) {
///         if (exception instanceof PlanNotFoundException) {
///             return Optional.of(new SimpleErrorCode(
///                 "PLAN_NOT_FOUND",
///                 "计划未找到",
///                 HttpStatus.NOT_FOUND.value()
///             ));
///         return Optional.empty();
/// ```
///
/// ## 设计原则
///
/// - **不可变性** - 所有模型类都是不可变的,线程安全
///   - **类型安全** - 使用强类型而非简单字符串
///   - **扩展性** - 支持自定义错误码实现
///
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.core.error.model;
