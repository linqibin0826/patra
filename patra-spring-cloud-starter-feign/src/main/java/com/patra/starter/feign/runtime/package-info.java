/// Patra Feign 运行时配置包。
///
/// 提供 Spring Cloud OpenFeign 的自动配置和增强功能，包括请求拦截、服务标识传播和基于约定的客户端扫描。
///
/// ## 职责
///
/// - 自动配置 Feign 客户端运行时组件
///   - 注册 {@link PatraFeignRequestInterceptor} 拦截器
///   - 按约定扫描 `com.patra` 包下的 `@FeignClient` 接口
///   - 加载并验证 `patra.feign.*` 配置属性
///
/// ## 核心组件
///
/// - {@link PatraFeignAutoConfiguration} - Feign 自动配置类
///   - {@link PatraFeignRequestInterceptor} - 请求拦截器，传播调用者标识
///   - {@link PatraFeignProperties} - 绑定 `patra.feign.*` 属性
///
/// ## 约定优于配置
///
/// 自动扫描 `com.patra` 包下的所有 `@FeignClient` 接口。按照约定，标准 RPC 客户端应放置在：
///
/// ```
///
/// com.patra.{module}.api.rpc.client
/// └── {Service}RpcClient
///
/// ```
///
/// ## 使用方式
///
/// 添加依赖后，Starter 会自动配置，无需额外代码：
///
/// ```java
/// <!-- Maven -->
/// <dependency>
///     <groupId>com.patra</groupId>
///     <artifactId>patra-spring-cloud-starter-feign</artifactId>
/// </dependency>
/// ```
///
/// 在 `application.yml` 中配置 Feign 客户端：
///
/// ```java
/// patra:
///   feign:
///     enabled: true
///     caller-service-name: patra-ingest
///
/// # Spring Cloud LoadBalancer 配置
/// spring:
///   cloud:
///     openfeign:
///       client:
///         config:
///           default:
///             connect-timeout: 5000
///             read-timeout: 10000
/// ```
///
/// ## 请求拦截器功能
///
/// {@link PatraFeignRequestInterceptor} 自动为每个 Feign 请求添加：
///
/// - **X-Caller-Service** - 调用者服务名称（从配置或 `spring.application.name` 获取）
///   - **X-Request-Id** - 请求追踪标识符（如果当前线程绑定）
///
/// ## 扩展点
///
/// - 自定义 `RequestInterceptor` Bean 添加额外请求头
///   - 使用 `patra.feign.enabled=false` 禁用自动配置
///   - 在专用 Starter 中定义自己的 `@EnableFeignClients` 配置
///
/// ## 示例
///
/// ```java
/// // 定义 Feign 客户端
/// @FeignClient(name = "patra-registry", path = "/api/v1/registry")
/// public interface RegistryRpcClient {
///     @GetMapping("/provenances/{code")
///     ProvenanceResponse getProvenance(@PathVariable String code);
///
/// // 使用 Feign 客户端
/// @Service
/// @RequiredArgsConstructor
/// public class IngestService {
///     private final RegistryRpcClient registryClient;
///
///     public void ingest(String provenanceCode) {
///         ProvenanceResponse config = registryClient.getProvenance(provenanceCode);
///         // 处理逻辑...
/// ```
///
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.feign.runtime;
