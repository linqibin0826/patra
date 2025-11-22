/// 微服务间通信的内部 REST API 端点。
/// 
/// 此包包含驱动适配器,用于接收来自其他内部微服务(通过 Feign 客户端)的 HTTP 请求并将其转换为应用程序用例调用。此处的所有类都是六边形架构适配器层的一部分(外部 → 系统方向)。
/// 
/// ## API 受众
/// 
/// 这些端点设计用于**内部微服务通信**:
/// 
/// - 由其他微服务中的 Feign 客户端使用(例如,`patra-ingest-infra/integration/storage/`)
///   - 不适用于外部公共访问
///   - 与公共 API 相比可能具有宽松的安全约束
///   - 针对高吞吐量内部操作进行了优化
/// 
/// ## 职责
/// 
/// - 实现 `patra-object-storage-api` 中定义的 OpenAPI 端点契约
///   - 使用 `@Valid` 注解验证请求 DTO
///   - 委托给 `RecordUploadOrchestrator` 和其他编排器
///   - 将领域结果转换为 API 响应 DTO
///   - 将领域异常映射到 HTTP ProblemDetail 响应
/// 
/// ## 安全考虑
/// 
/// - 内部 API 应受网络级安全保护(例如,服务网格、VPC)
///   - 身份验证可以使用服务到服务令牌(例如,带有服务身份的 JWT)
///   - 考虑速率限制以防止配置错误的消费者
/// 
/// ## 未来扩展
/// 
/// 如果需要面向公众的 API,请使用适当的安全加固创建单独的 `adapter.rest.public` 包。
/// 
/// ## 示例
/// 
/// ```java
/// @RestController
/// @RequiredArgsConstructor
/// public class StorageEndpointImpl implements StorageEndpoint {
///     private final RecordUploadOrchestrator orchestrator;
/// 
///     @Override
///     public RecordUploadResponse recordUpload(@Valid @RequestBody UploadRecordRequest request) {
///         var command = buildCommand(request);
///         var result = orchestrator.execute(command);
///         return new RecordUploadResponse(result.metadataId(), result.recordedAt());
/// ```
/// 
/// @author linqibin
/// @since 0.1.0
package com.patra.objectstorage.adapter.rest.internal;
