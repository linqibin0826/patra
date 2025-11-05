/**
 * Web 错误处理扩展点 (SPI) 包。
 *
 * <p>本包定义 Web 层错误处理的扩展点接口,允许业务模块自定义 Web 特定的错误处理行为。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>定义 Web 特定的 ProblemDetail 字段扩展点
 *   <li>定义验证错误格式化扩展点
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link com.patra.starter.web.error.spi.WebProblemFieldContributor} - Web 特定的 ProblemDetail 字段扩展
 *   <li>{@link com.patra.starter.web.error.spi.ValidationErrorsFormatter} - 验证错误格式化器接口
 * </ul>
 *
 * <h2>扩展点详解</h2>
 *
 * <h3>WebProblemFieldContributor - Web 字段扩展</h3>
 *
 * <p>扩展自 {@code ProblemFieldContributor},额外提供 {@code HttpServletRequest} 上下文:
 *
 * <pre>{@code
 * @Component
 * public class RequestInfoContributor implements WebProblemFieldContributor {
 *
 *     @Override
 *     public void contribute(Map<String, Object> fields, Throwable exception,
 *                           HttpServletRequest request) {
 *         // 添加请求相关信息
 *         fields.put("method", request.getMethod());
 *         fields.put("path", request.getRequestURI());
 *         fields.put("query", request.getQueryString());
 *         fields.put("userAgent", request.getHeader("User-Agent"));
 *         fields.put("clientIp", getClientIp(request));
 *         fields.put("requestId", request.getHeader("X-Request-ID"));
 *     }
 *
 *     private String getClientIp(HttpServletRequest request) {
 *         String ip = request.getHeader("X-Forwarded-For");
 *         if (ip == null || ip.isEmpty()) {
 *             ip = request.getRemoteAddr();
 *         }
 *         return ip;
 *     }
 * }
 * }</pre>
 *
 * <p>生成的 ProblemDetail:
 *
 * <pre>{@code
 * {
 *   "type": "...",
 *   "status": 404,
 *   "method": "GET",                           // WebProblemFieldContributor
 *   "path": "/api/plans/123",                  // WebProblemFieldContributor
 *   "query": "include=metadata",               // WebProblemFieldContributor
 *   "userAgent": "Mozilla/5.0 ...",            // WebProblemFieldContributor
 *   "clientIp": "192.168.1.100",               // WebProblemFieldContributor
 *   "requestId": "req-abc123"                  // WebProblemFieldContributor
 * }
 * }</pre>
 *
 * <h3>ValidationErrorsFormatter - 验证错误格式化</h3>
 *
 * <p>自定义验证错误的格式化和脱敏逻辑:
 *
 * <pre>{@code
 * @Component
 * @Primary
 * public class CustomValidationErrorsFormatter implements ValidationErrorsFormatter {
 *
 *     private static final Set<String> SENSITIVE_FIELDS = Set.of(
 *         "password", "secret", "token", "apiKey", "phone", "idCard"
 *     );
 *
 *     @Override
 *     public List<ValidationError> formatWithMasking(BindingResult bindingResult) {
 *         return bindingResult.getFieldErrors().stream()
 *             .map(this::toValidationError)
 *             .toList();
 *     }
 *
 *     private ValidationError toValidationError(FieldError fieldError) {
 *         String field = fieldError.getField();
 *         String message = fieldError.getDefaultMessage();
 *
 *         // 脱敏敏感字段
 *         if (isSensitiveField(field)) {
 *             message = maskSensitiveMessage(message);
 *         }
 *
 *         return new ValidationError(field, message);
 *     }
 *
 *     private boolean isSensitiveField(String field) {
 *         return SENSITIVE_FIELDS.stream()
 *             .anyMatch(sensitive -> field.toLowerCase().contains(sensitive));
 *     }
 *
 *     private String maskSensitiveMessage(String message) {
 *         // 移除可能包含敏感值的引号内容
 *         return message.replaceAll("'.*?'", "'***'");
 *     }
 * }
 * }</pre>
 *
 * <h2>使用场景</h2>
 *
 * <h3>场景 1: 添加审计信息</h3>
 * <pre>{@code
 * @Component
 * public class AuditInfoContributor implements WebProblemFieldContributor {
 *     private final SecurityContext securityContext;
 *
 *     @Override
 *     public void contribute(Map<String, Object> fields, Throwable exception,
 *                           HttpServletRequest request) {
 *         // 添加用户信息(脱敏)
 *         String userId = securityContext.getCurrentUserId();
 *         if (userId != null) {
 *             fields.put("userId", maskUserId(userId));
 *         }
 *
 *         // 添加时间戳
 *         fields.put("serverTime", Instant.now().toString());
 *
 *         // 添加环境信息
 *         fields.put("environment", getEnvironment());
 *     }
 * }
 * }</pre>
 *
 * <h3>场景 2: 添加客户端信息</h3>
 * <pre>{@code
 * @Component
 * public class ClientInfoContributor implements WebProblemFieldContributor {
 *
 *     @Override
 *     public void contribute(Map<String, Object> fields, Throwable exception,
 *                           HttpServletRequest request) {
 *         // 提取客户端版本
 *         String clientVersion = request.getHeader("X-Client-Version");
 *         if (clientVersion != null) {
 *             fields.put("clientVersion", clientVersion);
 *         }
 *
 *         // 提取平台信息
 *         String platform = request.getHeader("X-Platform");
 *         if (platform != null) {
 *             fields.put("platform", platform);  // "iOS", "Android", "Web"
 *         }
 *
 *         // 提取设备信息
 *         String deviceId = request.getHeader("X-Device-ID");
 *         if (deviceId != null) {
 *             fields.put("deviceId", maskDeviceId(deviceId));
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h3>场景 3: 自定义验证错误格式</h3>
 * <pre>{@code
 * @Component
 * public class I18nValidationErrorsFormatter implements ValidationErrorsFormatter {
 *     private final MessageSource messageSource;
 *
 *     @Override
 *     public List<ValidationError> formatWithMasking(BindingResult bindingResult) {
 *         Locale locale = LocaleContextHolder.getLocale();
 *
 *         return bindingResult.getFieldErrors().stream()
 *             .map(error -> {
 *                 // 国际化错误消息
 *                 String message = messageSource.getMessage(
 *                     error.getCode(),
 *                     error.getArguments(),
 *                     error.getDefaultMessage(),
 *                     locale
 *                 );
 *
 *                 return new ValidationError(error.getField(), message);
 *             })
 *             .toList();
 *     }
 * }
 * }</pre>
 *
 * <h2>WebProblemFieldContributor vs ProblemFieldContributor</h2>
 *
 * <table border="1">
 *   <tr>
 *     <th>特性</th>
 *     <th>ProblemFieldContributor</th>
 *     <th>WebProblemFieldContributor</th>
 *   </tr>
 *   <tr>
 *     <td>作用域</td>
 *     <td>所有环境(Web/非 Web)</td>
 *     <td>仅 Web 环境</td>
 *   </tr>
 *   <tr>
 *     <td>参数</td>
 *     <td>fields, exception</td>
 *     <td>fields, exception, request</td>
 *   </tr>
 *   <tr>
 *     <td>用途</td>
 *     <td>添加通用字段(service, version 等)</td>
 *     <td>添加 Web 特定字段(path, method, userAgent 等)</td>
 *   </tr>
 *   <tr>
 *     <td>示例</td>
 *     <td>traceId, timestamp, service</td>
 *     <td>path, method, clientIp, requestId</td>
 *   </tr>
 * </table>
 *
 * <h2>执行顺序</h2>
 *
 * <pre>
 * ProblemDetailBuilder.build(...)
 *   ↓
 * 1. 应用 ProblemFieldContributor (核心扩展点)
 *    └─ 添加通用字段(service, version, traceId 等)
 *   ↓
 * 2. 应用 WebProblemFieldContributor (Web 特定扩展点)
 *    └─ 添加 Web 字段(path, method, userAgent 等)
 *   ↓
 * 完整 ProblemDetail
 * </pre>
 *
 * <h2>多实现支持</h2>
 *
 * <p>所有 SPI 接口都支持多实现,无优先级限制(并行应用):
 *
 * <pre>
 * WebProblemFieldContributor 实现
 *   ├─ RequestInfoContributor          - 添加请求信息
 *   ├─ AuditInfoContributor            - 添加审计信息
 *   └─ ClientInfoContributor           - 添加客户端信息
 *
 * 所有 Contributor 的字段都会被合并到 ProblemDetail
 * </pre>
 *
 * <h2>注意事项</h2>
 *
 * <ul>
 *   <li><strong>性能</strong> - Contributor 在每次错误时执行,避免重量级操作
 *   <li><strong>敏感信息</strong> - 脱敏用户 ID、IP 地址等敏感字段
 *   <li><strong>线程安全</strong> - Contributor 方法应该是线程安全的
 *   <li><strong>空值检查</strong> - request 参数可能为 null(非 Servlet 环境)
 * </ul>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.web.error.spi;
