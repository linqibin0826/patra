/**
 * Web 错误处理配置包。
 *
 * <p>本包提供 Web 层错误处理的自动配置和属性绑定,基于 RFC 7807 ProblemDetail 标准。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>配置全局 REST 异常处理器
 *   <li>注册 ProblemDetail 构建和适配组件
 *   <li>提供 Web 错误处理配置属性
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link com.patra.starter.web.error.config.WebErrorAutoConfiguration} - Web 错误处理自动配置
 *   <li>{@link com.patra.starter.web.error.config.WebErrorProperties} - Web 错误处理配置属性
 * </ul>
 *
 * <h2>配置属性</h2>
 *
 * <p>配置前缀: {@code patra.web.problem}
 *
 * <pre>{@code
 * patra:
 *   web:
 *     problem:
 *       enabled: true                                    # 是否启用 Web 错误处理
 *       type-base-url: https://api.patra.com/errors/     # ProblemDetail type URI 基础 URL
 *       include-stack: false                             # 是否包含堆栈跟踪(生产环境应为 false)
 * }</pre>
 *
 * <h2>自动配置内容</h2>
 *
 * <p>当 {@code patra.web.problem.enabled=true} 时,自动注册:
 *
 * <ul>
 *   <li>{@link com.patra.starter.web.error.handler.GlobalRestExceptionHandler} - 全局异常处理器
 *   <li>{@link com.patra.starter.web.error.adapter.DefaultProblemDetailAdapter} - ProblemDetail 适配器
 *   <li>{@link com.patra.starter.web.error.builder.ProblemDetailBuilder} - ProblemDetail 构建器
 *   <li>{@link com.patra.starter.web.error.formatter.DefaultValidationErrorsFormatter} - 验证错误格式化器
 * </ul>
 *
 * <h2>ProblemDetail Type URI</h2>
 *
 * <p>{@code type-base-url} 用于生成 RFC 7807 标准的错误类型 URI:
 *
 * <pre>{@code
 * // 配置
 * patra.web.problem.type-base-url: https://api.patra.com/errors/
 *
 * // 生成的 ProblemDetail
 * {
 *   "type": "https://api.patra.com/errors/plan-not-found",  // type-base-url + error-code
 *   "title": "Not Found",
 *   "status": 404,
 *   "detail": "计划未找到: 123"
 * }
 * }</pre>
 *
 * <h2>堆栈跟踪控制</h2>
 *
 * <pre>{@code
 * # 开发环境 - 包含堆栈跟踪便于调试
 * patra:
 *   web:
 *     problem:
 *       include-stack: true
 *
 * # 生产环境 - 禁用堆栈跟踪避免泄露内部信息
 * patra:
 *   web:
 *     problem:
 *       include-stack: false
 * }</pre>
 *
 * <h2>使用示例</h2>
 *
 * <h3>完整配置示例</h3>
 *
 * <pre>{@code
 * patra:
 *   web:
 *     problem:
 *       enabled: true
 *       type-base-url: https://api.patra.com/errors/
 *       include-stack: false
 *
 *   error:
 *     context-prefix: INGEST                      # 错误码前缀
 *     observation:
 *       enabled: true                             # 启用错误观测
 * }</pre>
 *
 * <h3>自定义 ProblemDetail 字段</h3>
 *
 * <pre>{@code
 * @Component
 * public class CustomProblemFieldContributor implements WebProblemFieldContributor {
 *     @Override
 *     public void contribute(Map<String, Object> fields, Throwable exception,
 *                           HttpServletRequest request) {
 *         fields.put("service", "patra-ingest");
 *         fields.put("environment", "production");
 *         fields.put("clientVersion", request.getHeader("X-Client-Version"));
 *     }
 * }
 * }</pre>
 *
 * <h2>条件激活</h2>
 *
 * <p>Web 错误处理自动配置的激活条件:
 *
 * <ul>
 *   <li>{@code spring-boot-starter-web} 在 classpath
 *   <li>{@code patra.web.problem.enabled=true}(默认启用)
 * </ul>
 *
 * <h2>集成测试</h2>
 *
 * <pre>{@code
 * @SpringBootTest
 * @AutoConfigureMockMvc
 * class ErrorHandlingTest {
 *
 *     @Autowired
 *     private MockMvc mockMvc;
 *
 *     @Test
 *     void shouldReturnProblemDetailWhenNotFound() throws Exception {
 *         mockMvc.perform(get("/api/plans/999"))
 *             .andExpect(status().isNotFound())
 *             .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
 *             .andExpect(jsonPath("$.type").exists())
 *             .andExpect(jsonPath("$.title").value("Not Found"))
 *             .andExpect(jsonPath("$.status").value(404))
 *             .andExpect(jsonPath("$.detail").exists())
 *             .andExpect(jsonPath("$.traceId").exists());
 *     }
 * }
 * }</pre>
 *
 * <h2>环境特定配置</h2>
 *
 * <pre>{@code
 * # application-dev.yml
 * patra:
 *   web:
 *     problem:
 *       include-stack: true                      # 开发环境显示堆栈
 *       type-base-url: http://localhost:8080/errors/
 *
 * # application-prod.yml
 * patra:
 *   web:
 *     problem:
 *       include-stack: false                     # 生产环境隐藏堆栈
 *       type-base-url: https://api.patra.com/errors/
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.web.error.config;
