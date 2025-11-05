/**
 * Storage 服务数据传输对象包。
 *
 * <p>本包定义了对象存储元数据服务(patra-storage)的内部 API 契约。所有 DTO 类都是六边形架构 API 层的一部分,
 * 作为微服务间通信的数据传输对象,确保内部服务集成的类型安全和契约稳定。
 *
 * <h2>核心 DTO</h2>
 *
 * <ul>
 *   <li>{@link com.patra.storage.api.dto.UploadRecordRequest} - 上传记录请求 DTO,包含文件元数据、业务上下文、存储位置等信息
 *   <li>{@link com.patra.storage.api.dto.RecordUploadResponse} - 上传记录响应 DTO,返回元数据 ID 和记录时间戳
 * </ul>
 *
 * <h2>设计原则</h2>
 *
 * <ul>
 *   <li><strong>不可变性</strong>: 使用 Java Record 确保 DTO 不可变,防止传输过程中数据被篡改
 *   <li><strong>数据验证</strong>: 使用 Jakarta Validation 约束保证数据完整性和业务规则
 *   <li><strong>向后兼容</strong>: 新增字段必须有默认值,确保老版本客户端兼容
 *   <li><strong>契约稳定</strong>: 已发布字段不得删除或修改类型,仅允许追加新字段
 *   <li><strong>防御性设计</strong>: 对可变字段(如 Map、List)执行防御性拷贝,防止外部修改
 * </ul>
 *
 * <h2>数据验证约束</h2>
 *
 * {@link com.patra.storage.api.dto.UploadRecordRequest} 包含以下验证规则:
 *
 * <ul>
 *   <li>{@code bucketName, objectKey, md5Hash, serviceName, businessType, businessId, providerType} - 不能为空
 *   <li>{@code fileSize} - 必须 >= 0
 *   <li>{@code contentType} - 最大 128 字符
 *   <li>{@code recordRemarks} - 最大 512 字符
 * </ul>
 *
 * <h2>使用场景</h2>
 *
 * <ul>
 *   <li><strong>内部 Feign 调用</strong>: patra-ingest、patra-registry 等服务通过 Feign 客户端调用 patra-storage
 *   <li><strong>元数据持久化</strong>: 文件上传到 MinIO/S3 后,调用 recordUpload 端点持久化元数据
 *   <li><strong>幂等性保障</strong>: 通过 storage_key 唯一约束防止重复记录
 * </ul>
 *
 * <h2>示例</h2>
 *
 * <pre>{@code
 * // 在其他微服务中调用 Storage 服务
 * @Service
 * @RequiredArgsConstructor
 * public class FileUploadService {
 *
 *     private final StorageClient storageClient;
 *
 *     public void recordUpload(String bucket, String key, long size, String md5) {
 *         UploadRecordRequest request = new UploadRecordRequest(
 *             bucket,                          // bucketName: "literature-files"
 *             key,                             // objectKey: "2024/01/article.pdf"
 *             size,                            // fileSize: 1024000
 *             "application/pdf",               // contentType
 *             md5,                             // md5Hash: "5d41402abc4b2a76b9719d911017c592"
 *             null,                            // sha256Hash(可选)
 *             "patra-ingest",                  // serviceName
 *             "literature_batch",              // businessType
 *             "batch-2024-01-15-001",          // businessId
 *             Map.of("pmcId", "PMC12345678"),  // correlationData
 *             "MINIO",                         // providerType
 *             null,                            // expiresAt(可选)
 *             "Initial upload"                 // recordRemarks(可选)
 *         );
 *
 *         RecordUploadResponse response = storageClient.recordUpload(request);
 *         log.info("Metadata ID: {}, Recorded at: {}",
 *             response.metadataId(), response.recordedAt());
 *     }
 * }
 * }</pre>
 *
 * <h2>相关文档</h2>
 *
 * <ul>
 *   <li>API 契约: {@link com.patra.storage.api.endpoint.StorageEndpoint}
 *   <li>Feign 客户端: {@link com.patra.storage.api.client.StorageClient}
 *   <li>模块文档: {@code patra-storage/README.md}
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
package com.patra.storage.api.dto;
