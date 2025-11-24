/// REST Client 拦截器包。
///
/// <p>本包提供 HTTP 客户端的拦截器实现。
///
/// ## 职责
///
/// - 提供 {@link LoggingInterceptor} 日志拦截器（基础设施层调试工具）
/// - 支持 Spring 标准的 {@link org.springframework.http.client.ClientHttpRequestInterceptor} 扩展机制
///
/// 注意: 追踪传播和指标收集功能已移至 patra-spring-boot-starter-observability，
/// 通过 Spring 标准的 {@link org.springframework.http.client.ClientHttpRequestInterceptor} 接口提供，
/// 符合关注点分离原则。
///
/// ## 核心组件
///
/// - {@link LoggingInterceptor} - 日志拦截器（可选，用于调试）
///
/// ## 拦截器扩展机制
///
/// ### Spring 标准拦截器
///
/// 外部模块（如 patra-spring-boot-starter-observability）可以注册 Spring 标准的
/// `ClientHttpRequestInterceptor` 来扩展 REST Client 功能：
///
/// ```java
/// @Component
/// @Order(10)
/// public class CustomInterceptor implements ClientHttpRequestInterceptor {
///     @Override
///     public ClientHttpResponse intercept(
///         HttpRequest request,
///         byte[] body,
///         ClientHttpRequestExecution execution
///     ) throws IOException {
///         // 前置逻辑
///         try {
///             ClientHttpResponse response = execution.execute(request, body);
///             // 后置逻辑
///             return response;
///         } catch (IOException e) {
///             // 异常处理
///             throw e;
///         }
///     }
/// }
/// ```
///
/// ### 为什么使用 Spring 标准接口？
///
/// 1. **生命周期管理**：单个方法内完整管理资源（如 Micrometer Observation）
/// 2. **可靠性**：使用 try-finally 确保资源一定会被释放
/// 3. **Spring Boot 最佳实践**：与官方 Observability 实现保持一致
/// 4. **无内存泄漏风险**：不需要 ThreadLocal 等跨方法状态管理
///
/// ## 使用示例
///
/// ### 启用/禁用日志拦截器
///
/// ```yaml
/// patra:
///   rest-client:
///     interceptors:
///       logging:
///         enabled: true         # 启用日志拦截器
///         log-headers: false    # 避免记录敏感 Headers
///         log-body: false       # 避免记录大文件
/// ```
///
/// ## 可观测性支持
///
/// 追踪传播和指标收集由 patra-spring-boot-starter-observability 提供：
/// - 添加 patra-spring-boot-starter-observability 依赖自动启用
/// - 通过 Spring 标准的 `ClientHttpRequestInterceptor` 无缝集成
/// - 符合关注点分离（SoC）原则
///
/// @author linqibin
/// @since 0.1.0
package com.patra.starter.restclient.interceptor;
