/**
 * ProblemDetail 构建器包。
 *
 * <p>本包提供 RFC 7807 ProblemDetail 响应的构建能力,负责创建标准化的错误响应体。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>构建符合 RFC 7807 标准的 ProblemDetail 对象
 *   <li>设置标准字段(type、title、status、detail、instance)
 *   <li>添加扩展字段(traceId、timestamp、path、errorCode 等)
 *   <li>应用所有 {@code ProblemFieldContributor} 和 {@code WebProblemFieldContributor}
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link com.patra.starter.web.error.builder.ProblemDetailBuilder} - ProblemDetail 构建器
 * </ul>
 *
 * <h2>构建流程</h2>
 *
 * <pre>
 * ErrorResolution + Exception + HttpServletRequest
 *   ↓
 * ProblemDetailBuilder.build(...)
 *   ↓
 * 1. 创建基础 ProblemDetail
 *    ├─ type: baseUrl + errorCode
 *    ├─ title: HTTP 状态文本
 *    ├─ status: HTTP 状态码
 *    ├─ detail: 错误消息
 *    └─ instance: 请求路径
 *   ↓
 * 2. 添加扩展字段
 *    ├─ traceId: 从 TraceProvider 提取
 *    ├─ timestamp: 当前时间戳
 *    ├─ path: 请求路径
 *    ├─ errorCode: 错误码
 *    └─ stack: 堆栈跟踪(可选)
 *   ↓
 * 3. 应用 ProblemFieldContributor (核心扩展点)
 *   ↓
 * 4. 应用 WebProblemFieldContributor (Web 特定扩展点)
 *   ↓
 * ProblemDetail (完整响应体)
 * </pre>
 *
 * <h2>使用示例</h2>
 *
 * <h3>基础使用</h3>
 * <pre>{@code
 * @Component
 * public class DefaultProblemDetailAdapter implements ProblemDetailAdapter {
 *     private final ErrorResolutionPipeline pipeline;
 *     private final ProblemDetailBuilder builder;
 *
 *     @Override
 *     public ProblemDetailResponse adapt(Throwable exception, HttpServletRequest request) {
 *         ErrorResolution resolution = pipeline.resolve(exception);
 *
 *         // 使用构建器创建 ProblemDetail
 *         ProblemDetail problemDetail = builder.build(resolution, exception, request);
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
 * <h3>构建结果示例</h3>
 * <pre>{@code
 * // 输入
 * ErrorResolution resolution = {
 *     errorCode: "PLAN_NOT_FOUND",
 *     httpStatus: 404,
 *     message: "计划未找到: ID=123"
 * };
 * Exception exception = new PlanNotFoundException(123L);
 * HttpServletRequest request = { uri: "/api/plans/123" };
 *
 * // 输出
 * ProblemDetail problemDetail = {
 *   "type": "https://api.patra.com/errors/plan-not-found",
 *   "title": "Not Found",
 *   "status": 404,
 *   "detail": "计划未找到: ID=123",
 *   "instance": "/api/plans/123",
 *   "traceId": "abc123def456",
 *   "timestamp": "2025-01-12T10:30:45.123Z",
 *   "path": "/api/plans/123",
 *   "errorCode": "PLAN_NOT_FOUND"
 * };
 * }</pre>
 *
 * <h2>扩展字段</h2>
 *
 * <h3>内置扩展字段</h3>
 * <ul>
 *   <li><strong>traceId</strong> - 分布式追踪 ID(从 TraceProvider 提取)
 *   <li><strong>timestamp</strong> - 错误发生时间戳(ISO-8601 格式)
 *   <li><strong>path</strong> - 请求路径
 *   <li><strong>errorCode</strong> - 平台错误码
 *   <li><strong>stack</strong> - 堆栈跟踪(仅当 {@code include-stack=true})
 * </ul>
 *
 * <h3>通过 Contributor 添加自定义字段</h3>
 * <pre>{@code
 * // 核心 Contributor(所有环境)
 * @Component
 * public class ServiceInfoContributor implements ProblemFieldContributor {
 *     @Override
 *     public void contribute(Map<String, Object> fields, Throwable exception) {
 *         fields.put("service", "patra-ingest");
 *         fields.put("version", "0.1.0");
 *     }
 * }
 *
 * // Web Contributor(仅 Web 环境)
 * @Component
 * public class RequestInfoContributor implements WebProblemFieldContributor {
 *     @Override
 *     public void contribute(Map<String, Object> fields, Throwable exception,
 *                           HttpServletRequest request) {
 *         fields.put("method", request.getMethod());
 *         fields.put("userAgent", request.getHeader("User-Agent"));
 *         fields.put("clientIp", request.getRemoteAddr());
 *     }
 * }
 *
 * // 生成的 ProblemDetail
 * {
 *   "type": "...",
 *   "status": 404,
 *   "service": "patra-ingest",        // 来自 ServiceInfoContributor
 *   "version": "0.1.0",                // 来自 ServiceInfoContributor
 *   "method": "GET",                   // 来自 RequestInfoContributor
 *   "userAgent": "...",                // 来自 RequestInfoContributor
 *   "clientIp": "192.168.1.100"        // 来自 RequestInfoContributor
 * }
 * }</pre>
 *
 * <h2>Type URI 生成</h2>
 *
 * <p>Type URI 格式: {@code {type-base-url}{kebab-case-error-code}}
 *
 * <pre>{@code
 * // 配置
 * patra.web.problem.type-base-url: https://api.patra.com/errors/
 *
 * // 错误码转换
 * "PLAN_NOT_FOUND"     → "plan-not-found"
 * "INVALID_PARAMETER"  → "invalid-parameter"
 *
 * // 生成的 Type URI
 * "https://api.patra.com/errors/plan-not-found"
 * "https://api.patra.com/errors/invalid-parameter"
 * }</pre>
 *
 * <h2>堆栈跟踪控制</h2>
 *
 * <pre>{@code
 * // 开发环境 - 包含堆栈跟踪
 * patra:
 *   web:
 *     problem:
 *       include-stack: true
 *
 * // 生成的 ProblemDetail
 * {
 *   "type": "...",
 *   "status": 500,
 *   "stack": [
 *     "com.patra.ingest.domain.exception.PlanNotFoundException: 计划未找到",
 *     "  at com.patra.ingest.app.PlanQueryService.findById(PlanQueryService.java:45)",
 *     "  at com.patra.ingest.adapter.rest.PlanController.getById(PlanController.java:78)"
 *   ]
 * }
 *
 * // 生产环境 - 隐藏堆栈跟踪
 * patra:
 *   web:
 *     problem:
 *       include-stack: false
 *
 * // 生成的 ProblemDetail(无 stack 字段)
 * {
 *   "type": "...",
 *   "status": 500
 * }
 * }</pre>
 *
 * <h2>线程安全性</h2>
 *
 * <ul>
 *   <li>ProblemDetailBuilder 是线程安全的
 *   <li>每次调用 {@code build()} 创建新的 ProblemDetail 实例
 *   <li>Contributor 的 {@code contribute()} 方法应该是线程安全的
 * </ul>
 *
 * <h2>性能优化</h2>
 *
 * <ul>
 *   <li>避免在 Contributor 中执行重量级操作(如数据库查询)
 *   <li>堆栈跟踪生成有性能开销,生产环境建议禁用
 *   <li>TraceProvider 提取应尽量轻量(优先从 MDC/ThreadLocal 获取)
 * </ul>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.web.error.builder;
