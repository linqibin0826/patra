/**
 * Storage 领域模型聚合根包。
 *
 * <p>本包包含 patra-object-storage 服务的聚合根实体,作为六边形架构领域层的核心组件。 聚合根封装了业务逻辑和不变性规则,是领域模型的入口点,所有外部操作都必须通过聚合根进行。
 *
 * <h2>核心聚合根</h2>
 *
 * <ul>
 *   <li>{@link com.patra.objectstorage.domain.model.aggregate.FileMetadata} - 文件元数据聚合根,
 *       表示存储在对象存储系统中的每个文件的完整元数据记录
 * </ul>
 *
 * <h2>聚合根职责</h2>
 *
 * <ul>
 *   <li><strong>封装业务逻辑</strong>: 实现文件元数据的创建、更新、删除等业务操作
 *   <li><strong>维护不变性</strong>: 确保聚合内部状态始终处于有效状态
 *   <li><strong>生命周期管理</strong>: 追踪文件状态(ACTIVE/DELETED)、上传时间、过期时间等
 *   <li><strong>审计追溯</strong>: 记录创建人、更新人、IP 地址等审计信息
 *   <li><strong>事务边界</strong>: 聚合根作为事务一致性边界,确保原子性操作
 * </ul>
 *
 * <h2>设计原则</h2>
 *
 * <ul>
 *   <li><strong>纯 Java 实现</strong>: 禁止依赖 Spring、MyBatis 等框架,保持领域层纯净
 *   <li><strong>工厂方法</strong>: 使用静态工厂方法({@code create/restore})创建实例,确保对象有效性
 *   <li><strong>不可变值对象</strong>: 聚合根内部使用值对象封装复杂属性,确保数据一致性
 *   <li><strong>防御性编程</strong>: 对外部输入进行严格验证,抛出明确异常
 *   <li><strong>领域事件</strong>: 状态变更时发布领域事件(未来扩展)
 * </ul>
 *
 * <h2>FileMetadata 聚合根结构</h2>
 *
 * <ul>
 *   <li><strong>标识符</strong>: {@code id} (Long, 数据库主键)
 *   <li><strong>存储定位</strong>: {@code storageKey} (StorageKey 值对象)
 *   <li><strong>文件属性</strong>: {@code fileSize, contentType, checksum}
 *   <li><strong>业务上下文</strong>: {@code context} (BusinessContext 值对象)
 *   <li><strong>生命周期</strong>: {@code status, uploadedAt, expiresAt, deletedAt}
 *   <li><strong>审计信息</strong>: {@code createdBy, updatedBy, ipAddress, version}
 * </ul>
 *
 * <h2>聚合根操作</h2>
 *
 * <strong>创建新聚合根</strong>:
 *
 * <pre>{@code
 * FileMetadata metadata = FileMetadata.create(
 *     new StorageKey("publication-files", "2024/01/article.pdf"),
 *     new FileSize(1024000L),
 *     new FileChecksum("5d41402abc4b2a76b9719d911017c592", null),
 *     new BusinessContext("patra-ingest", "publication_batch", "batch-001", Map.of()),
 *     StorageProvider.MINIO
 * )
 * .withContentType("application/pdf")
 * .withExpiresAt(Instant.parse("2025-12-31T23:59:59Z"))
 * .withRecordRemarks("Initial upload");
 *
 * repository.save(metadata);
 * }</pre>
 *
 * <strong>从持久化快照恢复</strong>:
 *
 * <pre>{@code
 * FileMetadata restored = FileMetadata.restore(
 *     123456L,                                     // id
 *     new StorageKey("bucket", "key"),            // storageKey
 *     new FileSize(1024000L),                     // fileSize
 *     "application/pdf",                          // contentType
 *     new FileChecksum("md5hash", null),          // checksum
 *     new BusinessContext(...),                   // context
 *     StorageProvider.MINIO,                      // provider
 *     FileStatus.ACTIVE,                          // status
 *     Instant.now(),                              // uploadedAt
 *     null,                                       // expiresAt
 *     null,                                       // deletedAt
 *     null,                                       // recordRemarks
 *     0L,                                         // version
 *     null,                                       // ipAddress
 *     Instant.now(),                              // createdAt
 *     1001L,                                      // createdBy
 *     "admin",                                    // createdByName
 *     Instant.now(),                              // updatedAt
 *     1001L,                                      // updatedBy
 *     "admin",                                    // updatedByName
 *     false                                       // deleted
 * );
 * }</pre>
 *
 * <strong>软删除操作</strong>:
 *
 * <pre>{@code
 * FileMetadata metadata = repository.findByStorageKey(storageKey)
 *     .orElseThrow(() -> new IllegalArgumentException("文件不存在"));
 *
 * metadata.markAsDeleted(1001L, "admin");
 * repository.save(metadata);
 * }</pre>
 *
 * <h2>聚合根生命周期</h2>
 *
 * <pre>
 * [创建] → ACTIVE → [使用中] → [过期检查] → [软删除] → DELETED
 *    ↓        ↓         ↓            ↓           ↓
 * create() save()  业务操作   isExpired()  markAsDeleted()
 * </pre>
 *
 * <h2>并发控制</h2>
 *
 * 聚合根使用乐观锁机制防止并发更新冲突:
 *
 * <ul>
 *   <li>{@code version} 字段作为乐观锁版本号
 *   <li>每次保存时自动递增版本号
 *   <li>并发更新时数据库抛出 {@code OptimisticLockException}
 *   <li>应用层需重试加载最新版本并重新执行操作
 * </ul>
 *
 * <h2>相关文档</h2>
 *
 * <ul>
 *   <li>值对象: {@link com.patra.objectstorage.domain.model.vo}
 *   <li>枚举类型: {@link com.patra.objectstorage.domain.model.enums}
 *   <li>仓储端口: {@link com.patra.objectstorage.domain.port.FileMetadataRepository}
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
package com.patra.objectstorage.domain.model.aggregate;
