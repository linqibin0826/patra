/**
 * Web 错误模型包。
 *
 * <p>本包定义 Web 层错误处理使用的领域模型。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>定义验证错误模型
 *   <li>提供统一的验证错误表示
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link com.patra.starter.web.error.model.ValidationError} - 验证错误记录
 * </ul>
 *
 * <h2>ValidationError 模型</h2>
 *
 * <pre>{@code
 * public record ValidationError(
 *     String field,       // 字段名(支持嵌套路径,如 "userInfo.email")
 *     String message      // 错误消息(人类可读)
 * ) {}
 * }</pre>
 *
 * <h2>使用示例</h2>
 *
 * <h3>创建验证错误</h3>
 * <pre>{@code
 * // 手动创建
 * ValidationError error = new ValidationError(
 *     "email",
 *     "邮箱格式不正确"
 * );
 *
 * // 通过格式化器创建
 * ValidationErrorsFormatter formatter = ...;
 * List<ValidationError> errors = formatter.formatWithMasking(bindingResult);
 * }</pre>
 *
 * <h3>在 ProblemDetail 中使用</h3>
 * <pre>{@code
 * @RestControllerAdvice
 * public class GlobalRestExceptionHandler {
 *
 *     @Override
 *     protected ResponseEntity<Object> handleMethodArgumentNotValid(
 *         MethodArgumentNotValidException ex,
 *         HttpHeaders headers,
 *         HttpStatusCode status,
 *         WebRequest request
 *     ) {
 *         List<ValidationError> errors = formatter.formatWithMasking(ex.getBindingResult());
 *
 *         ProblemDetail problem = ProblemDetail.forStatus(status);
 *         problem.setDetail("Validation failed");
 *         problem.setProperty("errors", errors);  // 添加验证错误列表
 *
 *         return ResponseEntity.status(status).body(problem);
 *     }
 * }
 * }</pre>
 *
 * <h3>JSON 序列化示例</h3>
 * <pre>{@code
 * // ValidationError 列表
 * List<ValidationError> errors = List.of(
 *     new ValidationError("email", "邮箱格式不正确"),
 *     new ValidationError("password", "密码长度不能小于 8"),
 *     new ValidationError("userInfo.name", "姓名不能为空")
 * );
 *
 * // JSON 输出
 * [
 *   {
 *     "field": "email",
 *     "message": "邮箱格式不正确"
 *   },
 *   {
 *     "field": "password",
 *     "message": "密码长度不能小于 8"
 *   },
 *   {
 *     "field": "userInfo.name",
 *     "message": "姓名不能为空"
 *   }
 * ]
 * }</pre>
 *
 * <h2>完整 ProblemDetail 响应</h2>
 *
 * <pre>{@code
 * {
 *   "type": "https://api.patra.com/errors/validation-failed",
 *   "title": "Bad Request",
 *   "status": 400,
 *   "detail": "Validation failed for object='createPlanRequest'",
 *   "instance": "/api/plans",
 *   "traceId": "abc123",
 *   "timestamp": "2025-01-12T10:30:45.123Z",
 *   "errors": [                                // ValidationError 列表
 *     {
 *       "field": "provenanceCode",
 *       "message": "Provenance code 不能为空"
 *     },
 *     {
 *       "field": "externalId",
 *       "message": "External ID 不能为空"
 *     }
 *   ]
 * }
 * }</pre>
 *
 * <h2>字段路径格式</h2>
 *
 * <h3>简单字段</h3>
 * <pre>{@code
 * field: "email"
 * }</pre>
 *
 * <h3>嵌套对象</h3>
 * <pre>{@code
 * field: "userInfo.email"
 * field: "address.city"
 * }</pre>
 *
 * <h3>集合元素</h3>
 * <pre>{@code
 * field: "items[0].name"
 * field: "orderItems[2].quantity"
 * }</pre>
 *
 * <h3>Map 键</h3>
 * <pre>{@code
 * field: "metadata[key1]"
 * }</pre>
 *
 * <h2>前端解析示例</h2>
 *
 * <h3>React 示例</h3>
 * <pre>{@code
 * // 处理验证错误响应
 * axios.post('/api/plans', data)
 *   .catch(error => {
 *     if (error.response?.status === 400) {
 *       const errors = error.response.data.errors || [];
 *
 *       // 转换为字段-错误映射
 *       const fieldErrors = errors.reduce((acc, err) => {
 *         acc[err.field] = err.message;
 *         return acc;
 *       }, {});
 *
 *       // 显示错误
 *       setFormErrors(fieldErrors);
 *     }
 *   });
 *
 * // 渲染错误消息
 * <input name="email" />
 * {formErrors.email && <span className="error">{formErrors.email}</span>}
 * }</pre>
 *
 * <h3>Vue 示例</h3>
 * <pre>{@code
 * // 处理验证错误
 * this.$http.post('/api/plans', data)
 *   .catch(error => {
 *     if (error.response?.status === 400) {
 *       const errors = error.response.data.errors || [];
 *       this.formErrors = errors.reduce((acc, err) => {
 *         acc[err.field] = err.message;
 *         return acc;
 *       }, {});
 *     }
 *   });
 * }</pre>
 *
 * <h2>扩展 ValidationError</h2>
 *
 * <p>如需添加更多字段(如错误码、参数等),可自定义模型:
 *
 * <pre>{@code
 * public record ExtendedValidationError(
 *     String field,
 *     String message,
 *     String code,              // 错误码(如 "NotNull", "Size")
 *     Object rejectedValue,     // 被拒绝的值(脱敏后)
 *     Map<String, Object> params  // 约束参数(如 min=8, max=100)
 * ) {}
 *
 * // 使用示例
 * {
 *   "field": "password",
 *   "message": "密码长度不能小于 8",
 *   "code": "Size",
 *   "rejectedValue": "***",
 *   "params": {"min": 8, "max": 100}
 * }
 * }</pre>
 *
 * <h2>设计原则</h2>
 *
 * <ul>
 *   <li><strong>简洁性</strong> - 只包含必要字段,避免冗余信息
 *   <li><strong>不可变性</strong> - 使用 Java 17 {@code record},线程安全
 *   <li><strong>可序列化</strong> - 直接支持 Jackson JSON 序列化
 *   <li><strong>前端友好</strong> - 字段路径格式便于前端解析和映射
 * </ul>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.web.error.model;
