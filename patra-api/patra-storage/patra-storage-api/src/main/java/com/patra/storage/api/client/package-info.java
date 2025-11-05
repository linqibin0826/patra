/**
 * Storage 服务 Feign 客户端包。
 *
 * <p>本包提供 patra-storage 服务的 OpenFeign 客户端接口,供其他微服务进行类型安全的 RPC 调用。
 * 所有客户端接口都是六边形架构 API 层的一部分,继承端点契约并添加 Feign 注解,实现服务间透明远程调用。
 *
 * <h2>核心客户端</h2>
 *
 * <ul>
 *   <li>{@link com.patra.storage.api.client.StorageClient} - Storage 服务 Feign 客户端,扩展 {@link com.patra.storage.api.endpoint.StorageEndpoint}
 * </ul>
 *
 * <h2>设计原则</h2>
 *
 * <ul>
 *   <li><strong>契约继承</strong>: 客户端接口继承端点契约,避免重复定义方法签名
 *   <li><strong>类型安全</strong>: 通过强类型 DTO 和接口,在编译期发现参数错误
 *   <li><strong>服务发现</strong>: 通过 {@code @FeignClient(name = "patra-storage")} 集成 Nacos 服务发现
 *   <li><strong>负载均衡</strong>: 自动使用 Spring Cloud LoadBalancer 在多个实例间分配请求
 *   <li><strong>熔断降级</strong>: 支持集成 Resilience4j 实现服务熔断和降级(未来扩展)
 * </ul>
 *
 * <h2>Feign 配置</h2>
 *
 * <ul>
 *   <li><strong>服务名</strong>: {@code name = "patra-storage"} - 对应 Nacos 注册的服务名
 *   <li><strong>上下文 ID</strong>: {@code contextId = "storageClient"} - 用于区分同一服务的多个客户端
 *   <li><strong>超时配置</strong>: 默认继承全局 Feign 配置,可通过 application.yml 自定义
 * </ul>
 *
 * <h2>集成步骤</h2>
 *
 * <strong>1. 添加 Maven 依赖</strong>:
 *
 * <pre>{@code
 * <dependency>
 *     <groupId>com.patra</groupId>
 *     <artifactId>patra-storage-api</artifactId>
 * </dependency>
 * }</pre>
 *
 * <strong>2. 启用 Feign 客户端</strong>:
 *
 * <pre>{@code
 * @SpringBootApplication
 * @EnableFeignClients(basePackages = "com.patra.storage.api.client")
 * public class MyApplication {
 *     public static void main(String[] args) {
 *         SpringApplication.run(MyApplication.class, args);
 *     }
 * }
 * }</pre>
 *
 * <strong>3. 注入客户端并调用</strong>:
 *
 * <pre>{@code
 * @Service
 * @RequiredArgsConstructor
 * public class FileUploadService {
 *
 *     private final StorageClient storageClient;
 *
 *     public void recordUpload(String bucket, String key, long size, String md5) {
 *         UploadRecordRequest request = new UploadRecordRequest(
 *             bucket, key, size, "application/pdf", md5, null,
 *             "patra-ingest", "literature_batch", "batch-001",
 *             Map.of(), "MINIO", null, null
 *         );
 *
 *         RecordUploadResponse response = storageClient.recordUpload(request);
 *         log.info("Metadata ID: {}, Recorded at: {}",
 *             response.metadataId(), response.recordedAt());
 *     }
 * }
 * }</pre>
 *
 * <h2>异常处理</h2>
 *
 * Feign 调用可能抛出以下异常:
 *
 * <ul>
 *   <li><strong>FeignException.NotFound(404)</strong>: 目标服务未注册或路径不存在
 *   <li><strong>FeignException.BadRequest(400)</strong>: 请求参数验证失败
 *   <li><strong>FeignException.InternalServerError(500)</strong>: 服务端内部错误
 *   <li><strong>RetryableException</strong>: 网络超时或连接失败(支持重试)
 * </ul>
 *
 * 建议使用 {@code try-catch} 或全局异常处理器捕获异常:
 *
 * <pre>{@code
 * try {
 *     storageClient.recordUpload(request);
 * } catch (FeignException.BadRequest e) {
 *     log.error("请求参数验证失败: {}", e.contentUTF8());
 * } catch (FeignException e) {
 *     log.error("调用 Storage 服务失败: status={}, message={}",
 *         e.status(), e.getMessage());
 * }
 * }</pre>
 *
 * <h2>配置示例</h2>
 *
 * <pre>{@code
 * # application.yml
 * feign:
 *   client:
 *     config:
 *       patra-storage:                  # 特定服务配置
 *         connectTimeout: 5000          # 连接超时 5 秒
 *         readTimeout: 10000            # 读取超时 10 秒
 *         loggerLevel: BASIC            # 日志级别(NONE/BASIC/HEADERS/FULL)
 * }</pre>
 *
 * <h2>相关文档</h2>
 *
 * <ul>
 *   <li>端点契约: {@link com.patra.storage.api.endpoint.StorageEndpoint}
 *   <li>DTO 定义: {@link com.patra.storage.api.dto}
 *   <li>OpenFeign 官方文档: <a href="https://spring.io/projects/spring-cloud-openfeign">spring.io/projects/spring-cloud-openfeign</a>
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
package com.patra.storage.api.client;
