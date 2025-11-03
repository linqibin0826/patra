/**
 * 微服务间通信的内部 REST API 端点。
 *
 * <p>此包包含驱动适配器,用于接收来自其他内部微服务(通过 Feign 客户端)的 HTTP 请求并将其转换为应用程序用例调用。此处的所有类都是六边形架构适配器层的一部分(外部 → 系统方向)。
 *
 * <h2>API 受众</h2>
 *
 * 这些端点设计用于<strong>内部微服务通信</strong>:
 *
 * <ul>
 *   <li>由其他微服务中的 Feign 客户端使用(例如,{@code patra-ingest-infra/integration/storage/})
 *   <li>不适用于外部公共访问
 *   <li>与公共 API 相比可能具有宽松的安全约束
 *   <li>针对高吞吐量内部操作进行了优化
 * </ul>
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>实现 {@code patra-storage-api} 中定义的 OpenAPI 端点契约
 *   <li>使用 {@code @Valid} 注解验证请求 DTO
 *   <li>委托给 {@code RecordUploadOrchestrator} 和其他编排器
 *   <li>将领域结果转换为 API 响应 DTO
 *   <li>将领域异常映射到 HTTP ProblemDetail 响应
 * </ul>
 *
 * <h2>安全考虑</h2>
 *
 * <ul>
 *   <li>内部 API 应受网络级安全保护(例如,服务网格、VPC)
 *   <li>身份验证可以使用服务到服务令牌(例如,带有服务身份的 JWT)
 *   <li>考虑速率限制以防止配置错误的消费者
 * </ul>
 *
 * <h2>未来扩展</h2>
 *
 * 如果需要面向公众的 API,请使用适当的安全加固创建单独的 {@code adapter.rest.public} 包。
 *
 * <h2>示例</h2>
 *
 * <pre>{@code
 * @RestController
 * @RequiredArgsConstructor
 * public class StorageEndpointImpl implements StorageEndpoint {
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
 * @author linqibin
 * @since 0.1.0
 */
package com.patra.storage.adapter.rest.internal;
