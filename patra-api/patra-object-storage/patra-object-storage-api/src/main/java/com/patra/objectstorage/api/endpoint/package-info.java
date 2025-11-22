/// Storage 服务内部 API 端点契约包。
///
/// 本包定义了 patra-object-storage 服务对内暴露的 REST API 端点契约。所有接口都是六边形架构 API 层的一部分, 作为 Feign
/// 客户端的远程调用契约,确保微服务间通信的类型安全和契约一致性。
///
/// ## 核心契约
///
/// - {@link com.patra.objectstorage.api.endpoint.StorageEndpoint} - 存储元数据记录的内部 API 契约,定义
// recordUpload
///       端点
///
/// ## 设计原则
///
/// - **契约优先**: 端点接口定义先于实现,确保 API 稳定性
///   - **Feign 兼容**: 使用 Spring Web 注解(@PostMapping 等),兼容 OpenFeign 客户端
///   - **类型安全**: 所有参数和返回值使用强类型 DTO,避免 Map/Object 等弱类型
///   - **版本化**: API 路径包含服务名,未来支持版本演进(如 /v2/internal/storage)
///   - **文档化**: 所有端点方法包含 Javadoc,说明用途、参数、返回值
///
/// ## 端点路径规范
///
/// - **基础路径**: `/internal/storage` (内部服务专用,不暴露给外部)
///   - **文件记录**: `POST /internal/storage/files/record` - 记录文件上传元数据
///
/// ## 实现约定
///
/// - **适配器层实现**: 由 `patra-object-storage-adapter` 模块提供具体实现
///   - **请求验证**: 使用 `@Valid` 注解触发 Jakarta Validation 验证
///   - **异常处理**: 实现类需映射领域异常为 HTTP ProblemDetail 响应
///   - **事务边界**: 端点实现类仅负责请求转换,事务由应用层编排器管理
///
/// ## 使用示例
///
/// **定义端点契约**:
///
/// ```java
/// public interface StorageEndpoint {
///
///     String BASE_PATH = "/internal/storage";
///
///     @PostMapping(value = BASE_PATH + "/files/record",
///                  consumes = MediaType.APPLICATION_JSON_VALUE)
///     RecordUploadResponse recordUpload(@RequestBody @Valid UploadRecordRequest request);
/// ```
///
/// **适配器层实现契约**:
///
/// ```java
/// @RestController
/// @RequiredArgsConstructor
/// public class StorageEndpointImpl implements StorageEndpoint {
///
///     private final RecordUploadOrchestrator orchestrator;
///
///     @Override
///     public RecordUploadResponse recordUpload(@Valid @RequestBody UploadRecordRequest request) {
///         var command = buildCommand(request);
///         var result = orchestrator.execute(command);
///         return new RecordUploadResponse(result.metadataId(), result.recordedAt());
/// ```
///
/// **客户端调用**:
///
/// ```java
/// @FeignClient(name = "patra-object-storage", contextId = "storageClient")
/// public interface StorageClient extends StorageEndpoint {
///
/// // 在其他微服务中注入使用
/// @Service
/// @RequiredArgsConstructor
/// public class MyService {
///
///     private final StorageClient storageClient;
///
///     public void uploadFile() {
///         var request = new UploadRecordRequest(...);
///         var response = storageClient.recordUpload(request);
/// ```
///
/// ## 相关文档
///
/// - DTO 定义: {@link com.patra.objectstorage.api.dto}
///   - Feign 客户端: {@link com.patra.objectstorage.api.client.StorageClient}
///   - 适配器实现: `patra-object-storage-adapter/adapter/rest/internal/StorageEndpointImpl`
///
/// @author linqibin
/// @since 0.1.0
package com.patra.objectstorage.api.endpoint;
