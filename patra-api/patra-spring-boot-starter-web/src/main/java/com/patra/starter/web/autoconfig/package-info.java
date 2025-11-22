/// Web 层自动配置包。
///
/// 本包提供 Spring MVC Web 层的自动配置,包括类型转换器、参数绑定等 Web 特性。
///
/// ## 职责
///
/// - 注册自定义 Spring Converter(如 String → ProvenanceCode)
///   - 配置 Web 层类型转换规则
///   - 支持领域类型在 `@PathVariable` 和 `@RequestParam` 中的自动绑定
///
/// ## 核心组件
///
/// - {@link com.patra.starter.web.autoconfig.WebConversionAutoConfiguration} - Web 类型转换自动配置
///
/// ## 内置转换器
///
/// - **String → ProvenanceCode** - 支持在 REST API 中直接使用 ProvenanceCode 参数
///
/// ## 使用示例
///
/// ### 自动类型转换
///
/// ```java
/// @RestController
/// @RequestMapping("/api/plans")
/// public class PlanController {
///
///     // ProvenanceCode 自动从 String 转换
///     @GetMapping("/by-source/{provenanceCode")
///     public ApiResponse<List<PlanResponse>> listBySource(
///         @PathVariable ProvenanceCode provenanceCode  // 自动转换
///     ) {
///         // provenanceCode 已是 ProvenanceCode 对象
///         List<PlanAggregate> plans = planService.findBySource(provenanceCode);
///         return ApiResponse.ok(plans.stream().map(PlanResponse::from).toList());
///
///     // 支持请求参数
///     @GetMapping("/search")
///     public ApiResponse<List<PlanResponse>> search(
///         @RequestParam ProvenanceCode source  // 同样支持自动转换
///     ) {
///         // ...
/// ```
///
/// ### 转换失败处理
///
/// ```
///
/// // 无效的 ProvenanceCode 请求
/// GET /api/plans/by-source/INVALID_CODE
///
/// // 自动返回 400 Bad Request
/// {
///   "type": "about:blank",
///   "title": "Bad Request",
///   "status": 400,
///   "detail": "Failed to convert value of type 'java.lang.String' to required type
// 'ProvenanceCode'",
///   "instance": "/api/plans/by-source/INVALID_CODE"
/// }
///
/// ```
///
/// ## 扩展自定义转换器
///
/// ```java
/// @Component
/// public class MyCustomConverter implements Converter<String, MyDomainType> {
///     @Override
///     public MyDomainType convert(String source) {
///         // 自定义转换逻辑
///         return MyDomainType.parse(source);
/// ```
///
/// Spring Boot 会自动发现并注册所有 `@Component` 标注的 `Converter` 实现。
///
/// ## 配置能力
///
/// 支持通过 Spring Boot 配置自定义 Web 行为:
///
/// ```java
/// spring:
///   mvc:
///     format:
///       date: yyyy-MM-dd              # 日期格式
///       date-time: iso                # 日期时间格式
///     throw-exception-if-no-handler-found: true  # 404 抛异常
/// ```
///
/// ## 设计原则
///
/// - **类型安全** - 使用强类型领域对象替代原始字符串
///   - **声明式** - 通过类型声明自动触发转换,无需手动解析
///   - **失败快速** - 转换失败立即返回 400 错误,避免进入业务逻辑
///
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.web.autoconfig;
