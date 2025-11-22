/// Web 错误处理配置包。
///
/// 本包提供 Web 层错误处理的自动配置和属性绑定,基于 RFC 7807 ProblemDetail 标准。
///
/// ## 职责
///
/// - 配置全局 REST 异常处理器
///   - 注册 ProblemDetail 构建和适配组件
///   - 提供 Web 错误处理配置属性
///
/// ## 核心组件
///
/// - {@link com.patra.starter.web.error.config.WebErrorAutoConfiguration} - Web 错误处理自动配置
///   - {@link com.patra.starter.web.error.config.WebErrorProperties} - Web 错误处理配置属性
///
/// ## 配置属性
///
/// 配置前缀: `patra.web.problem`
///
/// ```java
/// patra:
///   web:
///     problem:
///       enabled: true                                    # 是否启用 Web 错误处理
///       type-base-url: https://api.patra.com/errors/     # ProblemDetail type URI 基础 URL
///       include-stack: false                             # 是否包含堆栈跟踪(生产环境应为 false)
/// ```
///
/// ## 自动配置内容
///
/// 当 `patra.web.problem.enabled=true` 时,自动注册:
///
/// - {@link com.patra.starter.web.error.handler.GlobalRestExceptionHandler} - 全局异常处理器
///   - {@link com.patra.starter.web.error.adapter.DefaultProblemDetailAdapter} - ProblemDetail 适配器
///   - {@link com.patra.starter.web.error.builder.ProblemDetailBuilder} - ProblemDetail 构建器
///   - {@link com.patra.starter.web.error.formatter.DefaultValidationErrorsFormatter} - 验证错误格式化器
///
/// ## ProblemDetail Type URI
///
/// `type-base-url` 用于生成 RFC 7807 标准的错误类型 URI:
///
/// ```java
/// // 配置
/// patra.web.problem.type-base-url: https://api.patra.com/errors/
///
/// // 生成的 ProblemDetail
/// {
///   "type": "https://api.patra.com/errors/plan-not-found",  // type-base-url + error-code
///   "title": "Not Found",
///   "status": 404,
///   "detail": "计划未找到: 123"
/// ```
///
/// ## 堆栈跟踪控制
///
/// ```java
/// # 开发环境 - 包含堆栈跟踪便于调试
/// patra:
///   web:
///     problem:
///       include-stack: true
///
/// # 生产环境 - 禁用堆栈跟踪避免泄露内部信息
/// patra:
///   web:
///     problem:
///       include-stack: false
/// ```
///
/// ## 使用示例
///
/// ### 完整配置示例
///
/// ```java
/// patra:
///   web:
///     problem:
///       enabled: true
///       type-base-url: https://api.patra.com/errors/
///       include-stack: false
///
///   error:
///     context-prefix: INGEST                      # 错误码前缀
///     observation:
///       enabled: true                             # 启用错误观测
/// ```
///
/// ### 自定义 ProblemDetail 字段
///
/// ```java
/// @Component
/// public class CustomProblemFieldContributor implements WebProblemFieldContributor {
///     @Override
///     public void contribute(Map<String, Object> fields, Throwable exception,
///                           HttpServletRequest request) {
///         fields.put("service", "patra-ingest");
///         fields.put("environment", "production");
///         fields.put("clientVersion", request.getHeader("X-Client-Version"));
/// ```
///
/// ## 条件激活
///
/// Web 错误处理自动配置的激活条件:
///
/// - `spring-boot-starter-web` 在 classpath
///   - `patra.web.problem.enabled=true`(默认启用)
///
/// ## 集成测试
///
/// ```java
/// @SpringBootTest
/// @AutoConfigureMockMvc
/// class ErrorHandlingTest {
///
///     @Autowired
///     private MockMvc mockMvc;
///
///     @Test
///     void shouldReturnProblemDetailWhenNotFound() throws Exception {
///         mockMvc.perform(get("/api/plans/999"))
///             .andExpect(status().isNotFound())
///             .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
///             .andExpect(jsonPath("$.type").exists())
///             .andExpect(jsonPath("$.title").value("Not Found"))
///             .andExpect(jsonPath("$.status").value(404))
///             .andExpect(jsonPath("$.detail").exists())
///             .andExpect(jsonPath("$.traceId").exists());
/// ```
///
/// ## 环境特定配置
///
/// ```java
/// # application-dev.yml
/// patra:
///   web:
///     problem:
///       include-stack: true                      # 开发环境显示堆栈
///       type-base-url: http://localhost:8080/errors/
///
/// # application-prod.yml
/// patra:
///   web:
///     problem:
///       include-stack: false                     # 生产环境隐藏堆栈
///       type-base-url: https://api.patra.com/errors/
/// ```
///
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.web.error.config;
