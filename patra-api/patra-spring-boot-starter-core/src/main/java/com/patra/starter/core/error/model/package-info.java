/**
 * 错误处理模型包。
 *
 * <p>本包定义错误处理框架使用的核心领域模型,提供统一的错误表示和错误码抽象。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>定义标准化的错误解析结果模型
 *   <li>提供简单错误码实现
 *   <li>封装错误码、HTTP 状态码、错误消息等信息
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link com.patra.starter.core.error.model.ErrorResolution} - 错误解析结果,包含错误码和 HTTP 状态
 *   <li>{@link com.patra.starter.core.error.model.SimpleErrorCode} - 简单错误码实现
 * </ul>
 *
 * <h2>ErrorResolution 模型</h2>
 *
 * <p>{@code ErrorResolution} 是错误解析引擎的输出结果,包含以下信息:
 *
 * <ul>
 *   <li><strong>错误码</strong> - 机器可读的唯一标识符(如 {@code PLAN_NOT_FOUND})
 *   <li><strong>HTTP 状态码</strong> - 对应的 HTTP 响应码(如 404)
 *   <li><strong>错误消息</strong> - 人类可读的错误描述
 *   <li><strong>原始异常</strong> - 引发错误的原始 {@code Throwable}
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <h3>创建错误解析结果</h3>
 *
 * <pre>{@code
 * // 通过引擎自动解析
 * ErrorResolution resolution = errorResolutionEngine.resolve(exception);
 *
 * // 手动构建
 * ErrorResolution resolution = ErrorResolution.builder()
 *     .errorCode(new SimpleErrorCode("PLAN_NOT_FOUND", "计划未找到", 404))
 *     .httpStatus(404)
 *     .message("未找到 ID 为 123 的计划")
 *     .exception(exception)
 *     .build();
 * }</pre>
 *
 * <h3>使用错误解析结果</h3>
 *
 * <pre>{@code
 * @RestControllerAdvice
 * public class GlobalExceptionHandler {
 *     private final ErrorResolutionPipeline pipeline;
 *
 *     @ExceptionHandler(Exception.class)
 *     public ResponseEntity<ProblemDetail> handleException(Exception ex) {
 *         ErrorResolution resolution = pipeline.resolve(ex);
 *
 *         ProblemDetail problem = ProblemDetail.forStatus(resolution.getHttpStatus());
 *         problem.setDetail(resolution.getMessage());
 *         problem.setProperty("errorCode", resolution.getErrorCode().getCode());
 *
 *         return ResponseEntity
 *             .status(resolution.getHttpStatus())
 *             .body(problem);
 *     }
 * }
 * }</pre>
 *
 * <h3>创建简单错误码</h3>
 *
 * <pre>{@code
 * // 在 ErrorMappingContributor 中使用
 * @Component
 * public class MyErrorMappingContributor implements ErrorMappingContributor {
 *     @Override
 *     public Optional<ErrorCodeLike> mapException(Throwable exception) {
 *         if (exception instanceof PlanNotFoundException) {
 *             return Optional.of(new SimpleErrorCode(
 *                 "PLAN_NOT_FOUND",
 *                 "计划未找到",
 *                 HttpStatus.NOT_FOUND.value()
 *             ));
 *         }
 *         return Optional.empty();
 *     }
 * }
 * }</pre>
 *
 * <h2>设计原则</h2>
 *
 * <ul>
 *   <li><strong>不可变性</strong> - 所有模型类都是不可变的,线程安全
 *   <li><strong>类型安全</strong> - 使用强类型而非简单字符串
 *   <li><strong>扩展性</strong> - 支持自定义错误码实现
 * </ul>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.core.error.model;
