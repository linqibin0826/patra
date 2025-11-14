/**
 * 验证错误格式化器包。
 *
 * <p>本包提供 JSR-303 Bean Validation 验证错误的格式化和脱敏能力, 将 Spring Validation 的 {@code BindingResult}
 * 转换为统一的验证错误列表。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>格式化 Spring Validation 错误为统一结构
 *   <li>脱敏敏感字段(如密码、手机号等)
 *   <li>提取字段名、错误消息和错误码
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link com.patra.starter.web.error.formatter.DefaultValidationErrorsFormatter} - 默认验证错误格式化器
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <h3>自动格式化验证错误</h3>
 *
 * <pre>{@code
 * // 请求模型
 * public record CreatePlanRequest(
 *     @NotNull(message = "Provenance code 不能为空")
 *     ProvenanceCode provenanceCode,
 *
 *     @NotBlank(message = "External ID 不能为空")
 *     @Size(max = 100, message = "External ID 长度不能超过 100")
 *     String externalId,
 *
 *     @Email(message = "邮箱格式不正确")
 *     String email
 * ) {}
 *
 * // 控制器
 * @PostMapping("/api/plans")
 * public ApiResponse<PlanResponse> create(@Valid @RequestBody CreatePlanRequest request) {
 *     // 验证失败会抛出 MethodArgumentNotValidException
 *     // GlobalRestExceptionHandler 自动使用 ValidationErrorsFormatter 格式化
 * }
 *
 * // 无效请求
 * POST /api/plans
 * {
 *   "email": "invalid-email"
 * }
 *
 * // 格式化后的验证错误
 * {
 *   "type": "...",
 *   "status": 400,
 *   "errors": [
 *     {
 *       "field": "provenanceCode",
 *       "message": "Provenance code 不能为空"
 *     },
 *     {
 *       "field": "externalId",
 *       "message": "External ID 不能为空"
 *     },
 *     {
 *       "field": "email",
 *       "message": "邮箱格式不正确"
 *     }
 *   ]
 * }
 * }</pre>
 *
 * <h3>在异常处理器中使用</h3>
 *
 * <pre>{@code
 * @RestControllerAdvice
 * public class GlobalRestExceptionHandler {
 *     private final ValidationErrorsFormatter formatter;
 *
 *     @Override
 *     protected ResponseEntity<Object> handleMethodArgumentNotValid(
 *         MethodArgumentNotValidException ex,
 *         HttpHeaders headers,
 *         HttpStatusCode status,
 *         WebRequest request
 *     ) {
 *         // 使用格式化器处理验证错误
 *         List<ValidationError> errors = formatter.formatWithMasking(ex.getBindingResult());
 *
 *         ProblemDetail problem = ProblemDetail.forStatus(status);
 *         problem.setProperty("errors", errors);
 *
 *         return ResponseEntity.status(status).body(problem);
 *     }
 * }
 * }</pre>
 *
 * <h2>脱敏支持</h2>
 *
 * <p>自动检测并脱敏敏感字段:
 *
 * <pre>{@code
 * public record UserRequest(
 *     @NotBlank(message = "用户名不能为空")
 *     String username,
 *
 *     @NotBlank(message = "密码不能为空")
 *     @Size(min = 8, message = "密码长度不能小于 8")
 *     String password,
 *
 *     @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
 *     String phone
 * ) {}
 *
 * // 无效请求
 * POST /api/users
 * {
 *   "username": "",
 *   "password": "123",
 *   "phone": "invalid"
 * }
 *
 * // 格式化后的验证错误(敏感字段已脱敏)
 * {
 *   "errors": [
 *     {
 *       "field": "username",
 *       "message": "用户名不能为空"
 *     },
 *     {
 *       "field": "password",
 *       "message": "密码长度不能小于 8"            // 不暴露密码值
 *     },
 *     {
 *       "field": "phone",
 *       "message": "手机号格式不正确"              // 不暴露手机号值
 *     }
 *   ]
 * }
 * }</pre>
 *
 * <h2>自定义格式化器</h2>
 *
 * <pre>{@code
 * @Component
 * @Primary
 * public class CustomValidationErrorsFormatter implements ValidationErrorsFormatter {
 *
 *     private static final Set<String> SENSITIVE_FIELDS = Set.of(
 *         "password", "secret", "token", "apiKey", "phone", "email", "idCard"
 *     );
 *
 *     @Override
 *     public List<ValidationError> formatWithMasking(BindingResult bindingResult) {
 *         return bindingResult.getFieldErrors().stream()
 *             .map(error -> {
 *                 String field = error.getField();
 *                 String message = error.getDefaultMessage();
 *
 *                 // 脱敏敏感字段的错误消息
 *                 if (isSensitiveField(field)) {
 *                     message = maskSensitiveMessage(message);
 *                 }
 *
 *                 return new ValidationError(field, message);
 *             })
 *             .toList();
 *     }
 *
 *     private boolean isSensitiveField(String field) {
 *         return SENSITIVE_FIELDS.stream()
 *             .anyMatch(sensitive -> field.toLowerCase().contains(sensitive));
 *     }
 *
 *     private String maskSensitiveMessage(String message) {
 *         // 移除可能包含敏感信息的部分
 *         return message.replaceAll("'.*?'", "'***'");
 *     }
 * }
 * }</pre>
 *
 * <h2>ValidationError 模型</h2>
 *
 * <pre>{@code
 * public record ValidationError(
 *     String field,       // 字段名
 *     String message      // 错误消息
 * ) {}
 * }</pre>
 *
 * <h2>错误数量限制</h2>
 *
 * <p>GlobalRestExceptionHandler 限制最多返回 100 个验证错误:
 *
 * <pre>{@code
 * List<ValidationError> errors = formatter.formatWithMasking(bindingResult);
 *
 * if (errors.size() > 100) {
 *     log.warn("验证错误超出最大限制: total={}, 截断为 100", errors.size());
 *     errors = errors.subList(0, 100);
 * }
 * }</pre>
 *
 * <h2>国际化支持</h2>
 *
 * <p>配合 Spring 国际化实现多语言验证消息:
 *
 * <pre>{@code
 * # messages_zh_CN.properties
 * javax.validation.constraints.NotNull.message=不能为空
 * javax.validation.constraints.Size.message=长度必须在 {min} 到 {max} 之间
 * javax.validation.constraints.Email.message=邮箱格式不正确
 *
 * # messages_en_US.properties
 * javax.validation.constraints.NotNull.message=must not be null
 * javax.validation.constraints.Size.message=size must be between {min} and {max}
 * javax.validation.constraints.Email.message=must be a valid email
 * }</pre>
 *
 * <h2>嵌套对象验证</h2>
 *
 * <pre>{@code
 * public record CreateOrderRequest(
 *     @Valid
 *     @NotNull
 *     UserInfo userInfo,
 *
 *     @Valid
 *     @NotEmpty
 *     List<OrderItem> items
 * ) {}
 *
 * public record UserInfo(
 *     @NotBlank String name,
 *     @Email String email
 * ) {}
 *
 * // 验证错误包含嵌套路径
 * {
 *   "errors": [
 *     {"field": "userInfo.name", "message": "不能为空"},
 *     {"field": "userInfo.email", "message": "邮箱格式不正确"},
 *     {"field": "items[0].quantity", "message": "数量必须大于 0"}
 *   ]
 * }
 * }</pre>
 *
 * <h2>最佳实践</h2>
 *
 * <ol>
 *   <li><strong>自定义消息</strong> - 使用 {@code message} 属性提供清晰的错误描述
 *   <li><strong>敏感字段</strong> - 实现自定义格式化器脱敏敏感字段
 *   <li><strong>国际化</strong> - 使用 MessageSource 支持多语言
 *   <li><strong>嵌套验证</strong> - 使用 {@code @Valid} 触发嵌套对象验证
 *   <li><strong>分组验证</strong> - 使用验证组针对不同场景应用不同规则
 * </ol>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.web.error.formatter;
