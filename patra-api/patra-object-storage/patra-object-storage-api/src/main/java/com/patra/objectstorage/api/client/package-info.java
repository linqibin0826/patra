/// Storage 服务 Feign 客户端包。
/// 
/// 本包提供 patra-object-storage 服务的 OpenFeign 客户端接口,供其他微服务进行类型安全的 RPC 调用。 所有客户端接口都是六边形架构 API
/// 层的一部分,继承端点契约并添加 Feign 注解,实现服务间透明远程调用。
/// 
/// ## 核心客户端
/// 
/// - {@link com.patra.objectstorage.api.client.StorageClient} - Storage 服务 Feign 客户端,扩展 {@link
///       com.patra.objectstorage.api.endpoint.StorageEndpoint}
/// 
/// ## 设计原则
/// 
/// - **契约继承**: 客户端接口继承端点契约,避免重复定义方法签名
///   - **类型安全**: 通过强类型 DTO 和接口,在编译期发现参数错误
///   - **服务发现**: 通过 `@FeignClient(name = "patra-object-storage")` 集成 Nacos 服务发现
///   - **负载均衡**: 自动使用 Spring Cloud LoadBalancer 在多个实例间分配请求
///   - **熔断降级**: 支持集成 Resilience4j 实现服务熔断和降级(未来扩展)
/// 
/// ## Feign 配置
/// 
/// - **服务名**: `name = "patra-object-storage"` - 对应 Nacos 注册的服务名
///   - **上下文 ID**: `contextId = "storageClient"` - 用于区分同一服务的多个客户端
///   - **超时配置**: 默认继承全局 Feign 配置,可通过 application.yml 自定义
/// 
/// ## 集成步骤
/// 
/// **1. 添加 Maven 依赖**:
/// 
/// ```java
/// <dependency>
///     <groupId>com.patra</groupId>
///     <artifactId>patra-object-storage-api</artifactId>
/// </dependency>
/// ```
/// 
/// **2. 启用 Feign 客户端**:
/// 
/// ```java
/// @SpringBootApplication
/// @EnableFeignClients(basePackages = "com.patra.objectstorage.api.client")
/// public class MyApplication {
///     public static void main(String[] args) {
///         SpringApplication.run(MyApplication.class, args);
/// ```
/// 
/// **3. 注入客户端并调用**:
/// 
/// ```java
/// @Service
/// @RequiredArgsConstructor
/// public class FileUploadService {
/// 
///     private final StorageClient storageClient;
/// 
///     public void recordUpload(String bucket, String key, long size, String md5) {
///         UploadRecordRequest request = new UploadRecordRequest(
///             bucket, key, size, "application/pdf", md5, null,
///             "patra-ingest", "publication_batch", "batch-001",
///             Map.of(), "MINIO", null, null
///         );
/// 
///         RecordUploadResponse response = storageClient.recordUpload(request);
///         log.info("Metadata ID: {, Recorded at: {",
///             response.metadataId(), response.recordedAt());
/// ```
/// 
/// ## 异常处理
/// 
/// Feign 调用可能抛出以下异常:
/// 
/// - **FeignException.NotFound(404)**: 目标服务未注册或路径不存在
///   - **FeignException.BadRequest(400)**: 请求参数验证失败
///   - **FeignException.InternalServerError(500)**: 服务端内部错误
///   - **RetryableException**: 网络超时或连接失败(支持重试)
/// 
/// 建议使用 `try-catch` 或全局异常处理器捕获异常:
/// 
/// ```java
/// try {
///     storageClient.recordUpload(request); catch (FeignException.BadRequest e) {
///     log.error("请求参数验证失败: {", e.contentUTF8()); catch (FeignException e) {
///     log.error("调用 Storage 服务失败: status={, message={",
///         e.status(), e.getMessage());
/// ```
/// 
/// ## 配置示例
/// 
/// ```java
/// # application.yml
/// feign:
///   client:
///     config:
///       patra-object-storage:                  # 特定服务配置
///         connectTimeout: 5000          # 连接超时 5 秒
///         readTimeout: 10000            # 读取超时 10 秒
///         loggerLevel: BASIC            # 日志级别(NONE/BASIC/HEADERS/FULL)
/// ```
/// 
/// ## 相关文档
/// 
/// - 端点契约: {@link com.patra.objectstorage.api.endpoint.StorageEndpoint}
///   - DTO 定义: {@link com.patra.objectstorage.api.dto}
///   - OpenFeign 官方文档: <a
///       href="https://spring.io/projects/spring-cloud-openfeign">spring.io/projects/spring-cloud-openfeign</a>
/// 
/// @author linqibin
/// @since 0.1.0
package com.patra.objectstorage.api.client;
