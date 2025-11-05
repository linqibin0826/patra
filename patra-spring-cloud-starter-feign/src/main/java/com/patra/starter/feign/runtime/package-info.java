/**
 * Patra Feign 运行时配置包。
 *
 * <p>提供 Spring Cloud OpenFeign 的自动配置和增强功能，包括请求拦截、服务标识传播和基于约定的客户端扫描。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>自动配置 Feign 客户端运行时组件
 *   <li>注册 {@link PatraFeignRequestInterceptor} 拦截器
 *   <li>按约定扫描 {@code com.patra} 包下的 {@code @FeignClient} 接口
 *   <li>加载并验证 {@code patra.feign.*} 配置属性
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link PatraFeignAutoConfiguration} - Feign 自动配置类
 *   <li>{@link PatraFeignRequestInterceptor} - 请求拦截器，传播调用者标识
 *   <li>{@link PatraFeignProperties} - 绑定 {@code patra.feign.*} 属性
 * </ul>
 *
 * <h2>约定优于配置</h2>
 *
 * <p>自动扫描 {@code com.patra} 包下的所有 {@code @FeignClient} 接口。按照约定，标准 RPC
 * 客户端应放置在：
 *
 * <pre>
 * com.patra.{module}.api.rpc.client
 * └── {Service}RpcClient
 * </pre>
 *
 * <h2>使用方式</h2>
 *
 * <p>添加依赖后，Starter 会自动配置，无需额外代码：
 *
 * <pre>{@code
 * <!-- Maven -->
 * <dependency>
 *     <groupId>com.patra</groupId>
 *     <artifactId>patra-spring-cloud-starter-feign</artifactId>
 * </dependency>
 * }</pre>
 *
 * <p>在 {@code application.yml} 中配置 Feign 客户端：
 *
 * <pre>{@code
 * patra:
 *   feign:
 *     enabled: true
 *     caller-service-name: patra-ingest
 *
 * # Spring Cloud LoadBalancer 配置
 * spring:
 *   cloud:
 *     openfeign:
 *       client:
 *         config:
 *           default:
 *             connect-timeout: 5000
 *             read-timeout: 10000
 * }</pre>
 *
 * <h2>请求拦截器功能</h2>
 *
 * <p>{@link PatraFeignRequestInterceptor} 自动为每个 Feign 请求添加：
 *
 * <ul>
 *   <li><b>X-Caller-Service</b> - 调用者服务名称（从配置或 {@code spring.application.name} 获取）
 *   <li><b>X-Request-Id</b> - 请求追踪标识符（如果当前线程绑定）
 * </ul>
 *
 * <h2>扩展点</h2>
 *
 * <ul>
 *   <li>自定义 {@code RequestInterceptor} Bean 添加额外请求头
 *   <li>使用 {@code patra.feign.enabled=false} 禁用自动配置
 *   <li>在专用 Starter 中定义自己的 {@code @EnableFeignClients} 配置
 * </ul>
 *
 * <h2>示例</h2>
 *
 * <pre>{@code
 * // 定义 Feign 客户端
 * @FeignClient(name = "patra-registry", path = "/api/v1/registry")
 * public interface RegistryRpcClient {
 *     @GetMapping("/provenances/{code}")
 *     ProvenanceResponse getProvenance(@PathVariable String code);
 * }
 *
 * // 使用 Feign 客户端
 * @Service
 * @RequiredArgsConstructor
 * public class IngestService {
 *     private final RegistryRpcClient registryClient;
 *
 *     public void ingest(String provenanceCode) {
 *         ProvenanceResponse config = registryClient.getProvenance(provenanceCode);
 *         // 处理逻辑...
 *     }
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.feign.runtime;
