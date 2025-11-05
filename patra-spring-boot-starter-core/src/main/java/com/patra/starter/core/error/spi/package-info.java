/**
 * 错误处理框架扩展点 (SPI) 包。
 *
 * <p>本包定义错误处理框架的所有扩展点接口(Service Provider Interface),
 * 允许业务模块和自定义组件集成到错误处理管道中。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>定义错误映射扩展点
 *   <li>定义 ProblemDetail 字段扩展点
 *   <li>定义追踪上下文提取扩展点
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link com.patra.starter.core.error.spi.ErrorMappingContributor} - 自定义异常到错误码映射
 *   <li>{@link com.patra.starter.core.error.spi.ProblemFieldContributor} - 向 ProblemDetail 添加自定义字段
 *   <li>{@link com.patra.starter.core.error.spi.TraceProvider} - 提取分布式追踪上下文
 * </ul>
 *
 * <h2>扩展点详解</h2>
 *
 * <h3>ErrorMappingContributor - 错误映射扩展</h3>
 *
 * <p>允许业务模块定义自定义异常到错误码的映射规则:
 *
 * <pre>{@code
 * @Component
 * @Order(100)  // 优先级:数值越小越优先
 * public class MyErrorMappingContributor implements ErrorMappingContributor {
 *     @Override
 *     public Optional<ErrorCodeLike> mapException(Throwable exception) {
 *         if (exception instanceof PlanNotFoundException ex) {
 *             return Optional.of(new SimpleErrorCode(
 *                 "PLAN_NOT_FOUND",
 *                 "计划未找到: " + ex.getPlanId(),
 *                 HttpStatus.NOT_FOUND.value()
 *             ));
 *         }
 *         return Optional.empty();  // 传递给下一个 Contributor
 *     }
 * }
 * }</pre>
 *
 * <h3>ProblemFieldContributor - 字段扩展</h3>
 *
 * <p>向 RFC 7807 ProblemDetail 添加自定义字段:
 *
 * <pre>{@code
 * @Component
 * public class ServiceInfoContributor implements ProblemFieldContributor {
 *     @Override
 *     public void contribute(Map<String, Object> fields, Throwable exception) {
 *         fields.put("service", "patra-ingest");
 *         fields.put("version", "0.1.0");
 *         fields.put("timestamp", Instant.now().toString());
 *     }
 * }
 * }</pre>
 *
 * <p>生成的 ProblemDetail:
 *
 * <pre>{@code
 * {
 *   "type": "...",
 *   "title": "Not Found",
 *   "status": 404,
 *   "detail": "...",
 *   "service": "patra-ingest",      // 自定义字段
 *   "version": "0.1.0",              // 自定义字段
 *   "timestamp": "2025-01-12T10:30:45Z"  // 自定义字段
 * }
 * }</pre>
 *
 * <h3>TraceProvider - 追踪上下文提取</h3>
 *
 * <p>提取分布式追踪 ID(从 HTTP Header、MDC、SkyWalking 等):
 *
 * <pre>{@code
 * @Component
 * public class SkyWalkingTraceProvider implements TraceProvider {
 *     @Override
 *     public Optional<String> getCurrentTraceId() {
 *         // 从 SkyWalking 提取追踪 ID
 *         String traceId = TraceContext.traceId();
 *         return Optional.ofNullable(traceId);
 *     }
 * }
 * }</pre>
 *
 * <h2>SPI 加载机制</h2>
 *
 * <p>所有 SPI 实现都通过 Spring Bean 机制自动发现和注册:
 *
 * <ol>
 *   <li>实现 SPI 接口(如 {@code ErrorMappingContributor})
 *   <li>使用 {@code @Component} 注解标注为 Spring Bean
 *   <li>可选使用 {@code @Order} 控制优先级
 *   <li>框架自动扫描并注入到相应的组件中
 * </ol>
 *
 * <h2>多实现支持</h2>
 *
 * <p>所有 SPI 接口都支持多实现,按优先级链式调用:
 *
 * <pre>
 * ErrorMappingContributor 链
 *   ├─ MyBusinessErrorContributor (@Order(10))
 *   ├─ MyDomainErrorContributor (@Order(20))
 *   └─ MyInfraErrorContributor (@Order(30))
 *
 * 第一个返回非空结果的 Contributor 生效
 * </pre>
 *
 * <h2>使用场景</h2>
 *
 * <h3>场景 1: 领域特定错误映射</h3>
 * <pre>{@code
 * // patra-ingest-domain 模块
 * @Component
 * public class IngestErrorMappingContributor implements ErrorMappingContributor {
 *     @Override
 *     public Optional<ErrorCodeLike> mapException(Throwable exception) {
 *         return switch (exception) {
 *             case PlanNotFoundException e -> Optional.of(IngestErrors.PLAN_NOT_FOUND);
 *             case DuplicatePlanException e -> Optional.of(IngestErrors.PLAN_DUPLICATE);
 *             default -> Optional.empty();
 *         };
 *     }
 * }
 * }</pre>
 *
 * <h3>场景 2: 添加安全上下文</h3>
 * <pre>{@code
 * @Component
 * public class SecurityContextContributor implements ProblemFieldContributor {
 *     private final SecurityContext securityContext;
 *
 *     @Override
 *     public void contribute(Map<String, Object> fields, Throwable exception) {
 *         String userId = securityContext.getCurrentUserId();
 *         if (userId != null) {
 *             fields.put("userId", maskUserId(userId));  // 脱敏
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h3>场景 3: 多来源追踪 ID</h3>
 * <pre>{@code
 * @Component
 * @Order(10)  // 优先从 SkyWalking 提取
 * public class SkyWalkingTraceProvider implements TraceProvider {
 *     @Override
 *     public Optional<String> getCurrentTraceId() {
 *         return Optional.ofNullable(TraceContext.traceId());
 *     }
 * }
 *
 * @Component
 * @Order(20)  // 降级从 HTTP Header 提取
 * public class HeaderTraceProvider implements TraceProvider {
 *     @Override
 *     public Optional<String> getCurrentTraceId() {
 *         HttpServletRequest request = getCurrentRequest();
 *         return Optional.ofNullable(request.getHeader("X-Trace-ID"));
 *     }
 * }
 * }</pre>
 *
 * <h2>设计原则</h2>
 *
 * <ul>
 *   <li><strong>单一职责</strong> - 每个 SPI 接口只负责一个扩展点
 *   <li><strong>开闭原则</strong> - 对扩展开放,对修改封闭
 *   <li><strong>优先级机制</strong> - 通过 {@code @Order} 控制多实现的调用顺序
 *   <li><strong>失败安全</strong> - 所有 SPI 方法都使用 {@code Optional},避免 null 异常
 * </ul>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.core.error.spi;
