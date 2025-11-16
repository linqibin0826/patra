/**
 * Storage 服务内部 API 端点契约包。
 *
 * <p>本包定义了 patra-object-storage 服务对内暴露的 REST API 端点契约。所有接口都是六边形架构 API 层的一部分, 作为 Feign
 * 客户端的远程调用契约,确保微服务间通信的类型安全和契约一致性。
 *
 * <h2>核心契约</h2>
 *
 * <ul>
 *   <li>{@link com.patra.objectstorage.api.endpoint.StorageEndpoint} - 存储元数据记录的内部 API 契约,定义 recordUpload
 *       端点
 * </ul>
 *
 * <h2>设计原则</h2>
 *
 * <ul>
 *   <li><strong>契约优先</strong>: 端点接口定义先于实现,确保 API 稳定性
 *   <li><strong>Feign 兼容</strong>: 使用 Spring Web 注解(@PostMapping 等),兼容 OpenFeign 客户端
 *   <li><strong>类型安全</strong>: 所有参数和返回值使用强类型 DTO,避免 Map/Object 等弱类型
 *   <li><strong>版本化</strong>: API 路径包含服务名,未来支持版本演进(如 /v2/internal/storage)
 *   <li><strong>文档化</strong>: 所有端点方法包含 Javadoc,说明用途、参数、返回值
 * </ul>
 *
 * <h2>端点路径规范</h2>
 *
 * <ul>
 *   <li><strong>基础路径</strong>: {@code /internal/storage} (内部服务专用,不暴露给外部)
 *   <li><strong>文件记录</strong>: {@code POST /internal/storage/files/record} - 记录文件上传元数据
 * </ul>
 *
 * <h2>实现约定</h2>
 *
 * <ul>
 *   <li><strong>适配器层实现</strong>: 由 {@code patra-object-storage-adapter} 模块提供具体实现
 *   <li><strong>请求验证</strong>: 使用 {@code @Valid} 注解触发 Jakarta Validation 验证
 *   <li><strong>异常处理</strong>: 实现类需映射领域异常为 HTTP ProblemDetail 响应
 *   <li><strong>事务边界</strong>: 端点实现类仅负责请求转换,事务由应用层编排器管理
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <strong>定义端点契约</strong>:
 *
 * <pre>{@code
 * public interface StorageEndpoint {
 *
 *     String BASE_PATH = "/internal/storage";
 *
 *     @PostMapping(value = BASE_PATH + "/files/record",
 *                  consumes = MediaType.APPLICATION_JSON_VALUE)
 *     RecordUploadResponse recordUpload(@RequestBody @Valid UploadRecordRequest request);
 * }
 * }</pre>
 *
 * <strong>适配器层实现契约</strong>:
 *
 * <pre>{@code
 * @RestController
 * @RequiredArgsConstructor
 * public class StorageEndpointImpl implements StorageEndpoint {
 *
 *     private final RecordUploadOrchestrator orchestrator;
 *
 *     @Override
 *     public RecordUploadResponse recordUpload(@Valid @RequestBody UploadRecordRequest request) {
 *         var command = buildCommand(request);
 *         var result = orchestrator.execute(command);
 *         return new RecordUploadResponse(result.metadataId(), result.recordedAt());
 *     }
 * }
 * }</pre>
 *
 * <strong>客户端调用</strong>:
 *
 * <pre>{@code
 * @FeignClient(name = "patra-object-storage", contextId = "storageClient")
 * public interface StorageClient extends StorageEndpoint {}
 *
 * // 在其他微服务中注入使用
 * @Service
 * @RequiredArgsConstructor
 * public class MyService {
 *
 *     private final StorageClient storageClient;
 *
 *     public void uploadFile() {
 *         var request = new UploadRecordRequest(...);
 *         var response = storageClient.recordUpload(request);
 *     }
 * }
 * }</pre>
 *
 * <h2>相关文档</h2>
 *
 * <ul>
 *   <li>DTO 定义: {@link com.patra.objectstorage.api.dto}
 *   <li>Feign 客户端: {@link com.patra.objectstorage.api.client.StorageClient}
 *   <li>适配器实现: {@code patra-object-storage-adapter/adapter/rest/internal/StorageEndpointImpl}
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
package com.patra.objectstorage.api.endpoint;
