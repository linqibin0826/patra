/**
 * Web 层自动配置包。
 *
 * <p>本包提供 Spring MVC Web 层的自动配置,包括类型转换器、参数绑定等 Web 特性。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>注册自定义 Spring Converter(如 String → ProvenanceCode)
 *   <li>配置 Web 层类型转换规则
 *   <li>支持领域类型在 {@code @PathVariable} 和 {@code @RequestParam} 中的自动绑定
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link com.patra.starter.web.autoconfig.WebConversionAutoConfiguration} - Web 类型转换自动配置
 * </ul>
 *
 * <h2>内置转换器</h2>
 *
 * <ul>
 *   <li><strong>String → ProvenanceCode</strong> - 支持在 REST API 中直接使用 ProvenanceCode 参数
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <h3>自动类型转换</h3>
 *
 * <pre>{@code
 * @RestController
 * @RequestMapping("/api/plans")
 * public class PlanController {
 *
 *     // ProvenanceCode 自动从 String 转换
 *     @GetMapping("/by-source/{provenanceCode}")
 *     public ApiResponse<List<PlanResponse>> listBySource(
 *         @PathVariable ProvenanceCode provenanceCode  // 自动转换
 *     ) {
 *         // provenanceCode 已是 ProvenanceCode 对象
 *         List<PlanAggregate> plans = planService.findBySource(provenanceCode);
 *         return ApiResponse.ok(plans.stream().map(PlanResponse::from).toList());
 *     }
 *
 *     // 支持请求参数
 *     @GetMapping("/search")
 *     public ApiResponse<List<PlanResponse>> search(
 *         @RequestParam ProvenanceCode source  // 同样支持自动转换
 *     ) {
 *         // ...
 *     }
 * }
 * }</pre>
 *
 * <h3>转换失败处理</h3>
 *
 * <pre>
 * // 无效的 ProvenanceCode 请求
 * GET /api/plans/by-source/INVALID_CODE
 *
 * // 自动返回 400 Bad Request
 * {
 *   "type": "about:blank",
 *   "title": "Bad Request",
 *   "status": 400,
 *   "detail": "Failed to convert value of type 'java.lang.String' to required type 'ProvenanceCode'",
 *   "instance": "/api/plans/by-source/INVALID_CODE"
 * }
 * </pre>
 *
 * <h2>扩展自定义转换器</h2>
 *
 * <pre>{@code
 * @Component
 * public class MyCustomConverter implements Converter<String, MyDomainType> {
 *     @Override
 *     public MyDomainType convert(String source) {
 *         // 自定义转换逻辑
 *         return MyDomainType.parse(source);
 *     }
 * }
 * }</pre>
 *
 * <p>Spring Boot 会自动发现并注册所有 {@code @Component} 标注的 {@code Converter} 实现。
 *
 * <h2>配置能力</h2>
 *
 * <p>支持通过 Spring Boot 配置自定义 Web 行为:
 *
 * <pre>{@code
 * spring:
 *   mvc:
 *     format:
 *       date: yyyy-MM-dd              # 日期格式
 *       date-time: iso                # 日期时间格式
 *     throw-exception-if-no-handler-found: true  # 404 抛异常
 * }</pre>
 *
 * <h2>设计原则</h2>
 *
 * <ul>
 *   <li><strong>类型安全</strong> - 使用强类型领域对象替代原始字符串
 *   <li><strong>声明式</strong> - 通过类型声明自动触发转换,无需手动解析
 *   <li><strong>失败快速</strong> - 转换失败立即返回 400 错误,避免进入业务逻辑
 * </ul>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.web.autoconfig;
