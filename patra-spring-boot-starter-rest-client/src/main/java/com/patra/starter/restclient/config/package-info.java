/// REST Client 自动配置包。
///
/// 本包提供 Spring Boot 自动配置能力，为所有依赖 patra-spring-boot-starter-rest-client 的应用
/// 自动配置统一的 HTTP 客户端、拦截器链和相关属性绑定。
///
/// ## 职责
///
/// - 自动配置 RestClient 及其依赖的组件
/// - 注册日志、追踪、指标三个内置拦截器
/// - 绑定配置属性到 Bean 实例
/// - 支持拦截器的条件化创建（基于配置和依赖）
///
/// ## 核心组件
///
/// - {@link com.patra.starter.restclient.config.RestClientAutoConfiguration} - 主配置类
/// - {@link com.patra.starter.restclient.config.RestClientProperties} - 配置属性绑定
///
/// ## 自动配置内容
///
/// RestClientAutoConfiguration 自动配置以下 Bean:
///
/// | Bean 名称 | 类型 | 触发条件 |
/// |-----------|------|---------|
/// | `defaultRestClient` | `RestClient` | 无依赖 |
/// | `defaultHttpRequestFactory` | `JdkClientHttpRequestFactory` | 无依赖 |
/// | `loggingInterceptor` | `LoggingInterceptor` | `patra.rest-client.interceptors.logging.enabled=true`(默认) |
/// | `restClientTracingInterceptor` | `TracingInterceptor` | `patra.rest-client.interceptors.tracing.enabled=true`(默认) |
/// | `metricsInterceptor` | `MetricsInterceptor` | `patra.rest-client.interceptors.metrics.enabled=true`(默认) + MeterRegistry 存在 |
///
/// ## 拦截器执行顺序
///
/// 拦截器按 @Order 值从小到大执行:
///
/// ```
/// 1. MetricsInterceptor (@Order(10)) - 记录开始时间
///    ↓
/// 2. TracingInterceptor (@Order(50)) - 传播追踪上下文
///    ↓
/// 3. LoggingInterceptor (@Order(100)) - 记录请求/响应
/// ```
///
/// ## 配置属性前缀
///
/// - `patra.rest-client` - 全局配置
/// - `patra.rest-client.timeout` - 超时配置
/// - `patra.rest-client.interceptors.*` - 拦截器配置
/// - `patra.rest-client.clients.*` - 多客户端配置
///
/// ## Bean 命名说明
///
/// - `restClientTracingInterceptor` - 为避免与 patra-spring-boot-starter-core 中的
///   `errorResolutionTracingInterceptor` 冲突，此 Bean 以 `restClientTracingInterceptor` 命名
/// - 详见 troubleshooting-and-notes/configuration.md 中的"Bean 命名冲突解决"部分
///
/// ## 扩展点
///
/// ### 自定义拦截器
///
/// 实现 `ClientHttpRequestInterceptor` 并注册为 Bean，将自动添加到拦截器链:
///
/// ```java
/// @Component
/// @Order(25)  // 在 MetricsInterceptor 之后、TracingInterceptor 之前执行
/// public class CustomInterceptor implements ClientHttpRequestInterceptor {
///     // 实现逻辑
/// }
/// ```
///
/// ### 自定义 RestClient
///
/// 禁用自动配置并手动创建 Bean:
///
/// ```yaml
/// patra:
///   rest-client:
///     enabled: false
/// ```
///
/// ```java
/// @Configuration
/// public class CustomRestClientConfig {
///     @Bean
///     public RestClient myCustomRestClient() {
///         // 自定义实现
///     }
/// }
/// ```
///
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.restclient.config;
