/// Storage 服务数据传输对象包。
///
/// 本包定义了对象存储元数据服务(patra-object-storage)的内部 API 契约。所有 DTO 类都是六边形架构 API 层的一部分,
/// 作为微服务间通信的数据传输对象,确保内部服务集成的类型安全和契约稳定。
///
/// ## 核心 DTO
///
/// - {@link com.patra.objectstorage.api.dto.UploadRecordRequest} - 上传记录请求 DTO,包含文件元数据、业务上下文、存储位置等信息
///   - {@link com.patra.objectstorage.api.dto.RecordUploadResponse} - 上传记录响应 DTO,返回元数据 ID 和记录时间戳
///
/// ## 设计原则
///
/// - **不可变性**: 使用 Java Record 确保 DTO 不可变,防止传输过程中数据被篡改
///   - **数据验证**: 使用 Jakarta Validation 约束保证数据完整性和业务规则
///   - **向后兼容**: 新增字段必须有默认值,确保老版本客户端兼容
///   - **契约稳定**: 已发布字段不得删除或修改类型,仅允许追加新字段
///   - **防御性设计**: 对可变字段(如 Map、List)执行防御性拷贝,防止外部修改
///
/// ## 数据验证约束
///
/// {@link com.patra.objectstorage.api.dto.UploadRecordRequest} 包含以下验证规则:
///
/// - `bucketName, objectKey, md5Hash, serviceName, businessType, businessId, providerType`
///       - 不能为空
///   - `fileSize` - 必须 >= 0
///   - `contentType` - 最大 128 字符
///   - `recordRemarks` - 最大 512 字符
///
/// ## 使用场景
///
/// - **内部 Feign 调用**: patra-ingest、patra-registry 等服务通过 Feign 客户端调用 patra-object-storage
///   - **元数据持久化**: 文件上传到 MinIO/S3 后,调用 recordUpload 端点持久化元数据
///   - **幂等性保障**: 通过 storage_key 唯一约束防止重复记录
///
/// ## 示例
///
/// ```java
/// // 在其他微服务中调用 Storage 服务
/// @Service
/// @RequiredArgsConstructor
/// public class FileUploadService {
///
///     private final StorageClient storageClient;
///
///     public void recordUpload(String bucket, String key, long size, String md5) {
///         UploadRecordRequest request = new UploadRecordRequest(
///             bucket,                          // bucketName: "publication-files"
///             key,                             // objectKey: "2024/01/article.pdf"
///             size,                            // fileSize: 1024000
///             "application/pdf",               // contentType
///             md5,                             // md5Hash: "5d41402abc4b2a76b9719d911017c592"
///             null,                            // sha256Hash(可选)
///             "patra-ingest",                  // serviceName
///             "publication_batch",              // businessType
///             "batch-2024-01-15-001",          // businessId
///             Map.of("pmcId", "PMC12345678"),  // correlationData
///             "MINIO",                         // providerType
///             null,                            // expiresAt(可选)
///             "Initial upload"                 // recordRemarks(可选)
///         );
///
///         RecordUploadResponse response = storageClient.recordUpload(request);
///         log.info("Metadata ID: {, Recorded at: {",
///             response.metadataId(), response.recordedAt());
/// ```
///
/// ## 相关文档
///
/// - API 契约: {@link com.patra.objectstorage.api.endpoint.StorageEndpoint}
///   - Feign 客户端: {@link com.patra.objectstorage.api.client.StorageClient}
///   - 模块文档: `patra-object-storage/README.md`
///
/// @author linqibin
/// @since 0.1.0
package com.patra.objectstorage.api.dto;
