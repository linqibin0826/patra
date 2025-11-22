///
/// REST Client Starter 模块。
///
/// <p>提供统一的 REST Client 自动配置、超时控制、重试机制、拦截器和指标收集。
///
/// <h2>核心功能</h2>
/// <ul>
///   <li>默认 RestClient Bean 配置（基于 Spring RestClient + JDK 21 HttpClient）
///   <li>超时配置（连接/读取/写入超时）
///   <li>可选的重试逻辑（指数退避）
///   <li>拦截器（日志/追踪/指标）
///   <li>YAML 配置属性支持
///   <li>多客户端配置（按用途分组）
/// </ul>
///
/// <h2>使用示例</h2>
/// <pre>{@code
/// // 1. 添加 Maven 依赖
/// <dependency>
///     <groupId>com.patra</groupId>
///     <artifactId>patra-spring-boot-starter-rest-client</artifactId>
/// </dependency>
///
/// // 2. 配置 application.yml
/// patra:
///   rest-client:
///     timeout:
///       connect: 10s
///       read: 30s
///     interceptors:
///       logging:
///         enabled: true
///
/// // 3. 注入使用
/// @Component
/// public class MyService {
///     @Autowired
///     private RestClient defaultRestClient;
///
///     public String callExternalApi() {
///         return defaultRestClient.get()
///             .uri("https://api.example.com/data")
///             .retrieve()
///             .body(String.class);
///     }
/// }
/// }</pre>
///
/// @author linqibin
/// @since 0.1.0
///
package com.patra.starter.restclient;
