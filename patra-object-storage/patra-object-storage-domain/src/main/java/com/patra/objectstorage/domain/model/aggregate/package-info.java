/// Storage 领域模型聚合根包。
///
/// 本包包含 patra-object-storage 服务的聚合根实体,作为六边形架构领域层的核心组件。 聚合根封装了业务逻辑和不变性规则,是领域模型的入口点,所有外部操作都必须通过聚合根进行。
///
/// ## 核心聚合根
///
/// - {@link com.patra.objectstorage.domain.model.aggregate.FileMetadata} - 文件元数据聚合根,
///       表示存储在对象存储系统中的每个文件的完整元数据记录
///
/// ## 聚合根职责
///
/// - **封装业务逻辑**: 实现文件元数据的创建、更新、删除等业务操作
///   - **维护不变性**: 确保聚合内部状态始终处于有效状态
///   - **生命周期管理**: 追踪文件状态(ACTIVE/DELETED)、上传时间、过期时间等
///   - **审计追溯**: 记录创建人、更新人、IP 地址等审计信息
///   - **事务边界**: 聚合根作为事务一致性边界,确保原子性操作
///
/// ## 设计原则
///
/// - **纯 Java 实现**: 禁止依赖 Spring、MyBatis 等框架,保持领域层纯净
///   - **工厂方法**: 使用静态工厂方法(`create/restore`)创建实例,确保对象有效性
///   - **不可变值对象**: 聚合根内部使用值对象封装复杂属性,确保数据一致性
///   - **防御性编程**: 对外部输入进行严格验证,抛出明确异常
///   - **领域事件**: 状态变更时发布领域事件(未来扩展)
///
/// ## FileMetadata 聚合根结构
///
/// - **标识符**: `id` (Long, 数据库主键)
///   - **存储定位**: `storageKey` (StorageKey 值对象)
///   - **文件属性**: `fileSize, contentType, checksum`
///   - **业务上下文**: `context` (BusinessContext 值对象)
///   - **生命周期**: `status, uploadedAt, expiresAt, deletedAt`
///   - **审计信息**: `createdBy, updatedBy, ipAddress, version`
///
/// ## 聚合根操作
///
/// **创建新聚合根**:
///
/// ```java
/// FileMetadata metadata = FileMetadata.create(
///     new StorageKey("publication-files", "2024/01/article.pdf"),
///     new FileSize(1024000L),
///     new FileChecksum("5d41402abc4b2a76b9719d911017c592", null),
///     new BusinessContext("patra-ingest", "publication_batch", "batch-001", Map.of()),
///     StorageProvider.MINIO
/// )
/// .withContentType("application/pdf")
/// .withExpiresAt(Instant.parse("2025-12-31T23:59:59Z"))
/// .withRecordRemarks("Initial upload");
///
/// repository.save(metadata);
/// ```
///
/// **从持久化快照恢复**:
///
/// ```java
/// FileMetadata restored = FileMetadata.restore(
///     123456L,                                     // id
///     new StorageKey("bucket", "key"),            // storageKey
///     new FileSize(1024000L),                     // fileSize
///     "application/pdf",                          // contentType
///     new FileChecksum("md5hash", null),          // checksum
///     new BusinessContext(...),                   // context
///     StorageProvider.MINIO,                      // provider
///     FileStatus.ACTIVE,                          // status
///     Instant.now(),                              // uploadedAt
///     null,                                       // expiresAt
///     null,                                       // deletedAt
///     null,                                       // recordRemarks
///     0L,                                         // version
///     null,                                       // ipAddress
///     Instant.now(),                              // createdAt
///     1001L,                                      // createdBy
///     "admin",                                    // createdByName
///     Instant.now(),                              // updatedAt
///     1001L,                                      // updatedBy
///     "admin",                                    // updatedByName
///     false                                       // deleted
/// );
/// ```
///
/// **软删除操作**:
///
/// ```java
/// FileMetadata metadata = repository.findByStorageKey(storageKey)
///     .orElseThrow(() -> new IllegalArgumentException("文件不存在"));
///
/// metadata.markAsDeleted(1001L, "admin");
/// repository.save(metadata);
/// ```
///
/// ## 聚合根生命周期
///
/// ```
///
/// [创建] → ACTIVE → [使用中] → [过期检查] → [软删除] → DELETED
///    ↓        ↓         ↓            ↓           ↓
/// create() save()  业务操作   isExpired()  markAsDeleted()
///
/// ```
///
/// ## 并发控制
///
/// 聚合根使用乐观锁机制防止并发更新冲突:
///
/// - `version` 字段作为乐观锁版本号
///   - 每次保存时自动递增版本号
///   - 并发更新时数据库抛出 `OptimisticLockException`
///   - 应用层需重试加载最新版本并重新执行操作
///
/// ## 相关文档
///
/// - 值对象: {@link com.patra.objectstorage.domain.model.vo}
///   - 枚举类型: {@link com.patra.objectstorage.domain.model.enums}
///   - 仓储端口: {@link com.patra.objectstorage.domain.port.FileMetadataRepository}
///
/// @author linqibin
/// @since 0.1.0
package com.patra.objectstorage.domain.model.aggregate;
