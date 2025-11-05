package com.patra.storage.domain.model.aggregate;

import com.patra.storage.domain.model.enums.FileStatus;
import com.patra.storage.domain.model.enums.StorageProvider;
import com.patra.storage.domain.model.vo.BusinessContext;
import com.patra.storage.domain.model.vo.FileChecksum;
import com.patra.storage.domain.model.vo.FileSize;
import com.patra.storage.domain.model.vo.StorageKey;
import java.time.Instant;
import java.util.Map;

/**
 * FileMetadata 测试数据构建器。
 *
 * <p>使用 Builder 模式简化测试用例中的聚合根构建。
 *
 * <h3>使用示例</h3>
 *
 * <pre>{@code
 * // 创建活跃状态的文件元数据
 * FileMetadata metadata = FileMetadataTestDataBuilder.anActiveFile().build();
 *
 * // 创建已删除的文件元数据
 * FileMetadata metadata = FileMetadataTestDataBuilder.aDeletedFile()
 *     .deletedAt(Instant.now())
 *     .buildRestored();
 *
 * // 创建完全自定义的文件元数据
 * FileMetadata metadata = FileMetadataTestDataBuilder.anActiveFile()
 *     .id(1001L)
 *     .storageKey(new StorageKey("my-bucket", "my-file.pdf"))
 *     .fileSize(new FileSize(2 * 1024 * 1024))
 *     .provider(StorageProvider.S3)
 *     .buildRestored();
 * }</pre>
 *
 * @author linqibin
 * @since 0.2.0
 */
public class FileMetadataTestDataBuilder {

  // ========== 核心字段 ==========
  private Long id;
  private StorageKey storageKey = new StorageKey("test-bucket", "test/file.pdf");
  private FileSize fileSize = new FileSize(1024 * 1024); // 1 MB
  private String contentType;
  private FileChecksum checksum = new FileChecksum("d41d8cd98f00b204e9800998ecf8427e", null);
  private BusinessContext context =
      new BusinessContext("patra-ingest", "literature_batch", "batch-001", null);
  private StorageProvider provider = StorageProvider.MINIO;
  private FileStatus status = FileStatus.ACTIVE;

  // ========== 时间戳字段 ==========
  private Instant uploadedAt = Instant.parse("2024-01-01T10:00:00Z");
  private Instant expiresAt;
  private Instant deletedAt;

  // ========== 审计字段 ==========
  private String recordRemarks;
  private Long version = 0L;
  private byte[] ipAddress;
  private Instant createdAt = Instant.parse("2024-01-01T09:55:00Z");
  private Long createdBy = 1001L;
  private String createdByName = "张三";
  private Instant updatedAt = Instant.parse("2024-01-01T10:00:00Z");
  private Long updatedBy = 1001L;
  private String updatedByName = "张三";
  private Boolean deleted = Boolean.FALSE;

  // ========== 构造函数（私有） ==========

  private FileMetadataTestDataBuilder() {}

  // ========== 静态工厂方法 ==========

  /**
   * 创建默认的活跃状态文件构建器。
   *
   * <p>包含以下默认配置：
   *
   * <ul>
   *   <li>状态: ACTIVE
   *   <li>存储桶: test-bucket
   *   <li>对象键: test/file.pdf
   *   <li>文件大小: 1 MB
   *   <li>存储提供商: MINIO
   *   <li>业务上下文: patra-ingest/literature_batch/batch-001
   *   <li>校验和: MD5 (示例哈希)
   * </ul>
   *
   * @return 文件元数据构建器
   */
  public static FileMetadataTestDataBuilder anActiveFile() {
    return new FileMetadataTestDataBuilder().status(FileStatus.ACTIVE).deleted(Boolean.FALSE);
  }

  /**
   * 创建默认的已删除文件构建器。
   *
   * <p>包含以下默认配置：
   *
   * <ul>
   *   <li>状态: DELETED
   *   <li>软删除标志: true
   *   <li>删除时间: 当前时间
   *   <li>其他字段同 {@link #anActiveFile()}
   * </ul>
   *
   * @return 文件元数据构建器
   */
  public static FileMetadataTestDataBuilder aDeletedFile() {
    return new FileMetadataTestDataBuilder()
        .status(FileStatus.DELETED)
        .deleted(Boolean.TRUE)
        .deletedAt(Instant.now());
  }

  /**
   * 创建默认的已过期文件构建器。
   *
   * <p>包含以下默认配置：
   *
   * <ul>
   *   <li>状态: EXPIRED
   *   <li>过期时间: 1 小时前
   *   <li>其他字段同 {@link #anActiveFile()}
   * </ul>
   *
   * @return 文件元数据构建器
   */
  public static FileMetadataTestDataBuilder anExpiredFile() {
    return new FileMetadataTestDataBuilder()
        .status(FileStatus.EXPIRED)
        .expiresAt(Instant.now().minusSeconds(3600)); // 1 小时前
  }

  // ========== Builder 方法 ==========

  public FileMetadataTestDataBuilder id(Long id) {
    this.id = id;
    return this;
  }

  public FileMetadataTestDataBuilder storageKey(StorageKey storageKey) {
    this.storageKey = storageKey;
    return this;
  }

  public FileMetadataTestDataBuilder fileSize(FileSize fileSize) {
    this.fileSize = fileSize;
    return this;
  }

  public FileMetadataTestDataBuilder contentType(String contentType) {
    this.contentType = contentType;
    return this;
  }

  public FileMetadataTestDataBuilder checksum(FileChecksum checksum) {
    this.checksum = checksum;
    return this;
  }

  public FileMetadataTestDataBuilder context(BusinessContext context) {
    this.context = context;
    return this;
  }

  public FileMetadataTestDataBuilder provider(StorageProvider provider) {
    this.provider = provider;
    return this;
  }

  public FileMetadataTestDataBuilder status(FileStatus status) {
    this.status = status;
    return this;
  }

  public FileMetadataTestDataBuilder uploadedAt(Instant uploadedAt) {
    this.uploadedAt = uploadedAt;
    return this;
  }

  public FileMetadataTestDataBuilder expiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
    return this;
  }

  public FileMetadataTestDataBuilder deletedAt(Instant deletedAt) {
    this.deletedAt = deletedAt;
    return this;
  }

  public FileMetadataTestDataBuilder recordRemarks(String recordRemarks) {
    this.recordRemarks = recordRemarks;
    return this;
  }

  public FileMetadataTestDataBuilder version(Long version) {
    this.version = version;
    return this;
  }

  public FileMetadataTestDataBuilder ipAddress(byte[] ipAddress) {
    this.ipAddress = ipAddress;
    return this;
  }

  public FileMetadataTestDataBuilder createdAt(Instant createdAt) {
    this.createdAt = createdAt;
    return this;
  }

  public FileMetadataTestDataBuilder createdBy(Long createdBy) {
    this.createdBy = createdBy;
    return this;
  }

  public FileMetadataTestDataBuilder createdByName(String createdByName) {
    this.createdByName = createdByName;
    return this;
  }

  public FileMetadataTestDataBuilder updatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
    return this;
  }

  public FileMetadataTestDataBuilder updatedBy(Long updatedBy) {
    this.updatedBy = updatedBy;
    return this;
  }

  public FileMetadataTestDataBuilder updatedByName(String updatedByName) {
    this.updatedByName = updatedByName;
    return this;
  }

  public FileMetadataTestDataBuilder deleted(Boolean deleted) {
    this.deleted = deleted;
    return this;
  }

  // ========== 便捷配置方法 ==========

  /**
   * 配置完整的业务上下文，包含关联数据。
   *
   * @param serviceName 服务名称
   * @param businessType 业务类型
   * @param businessId 业务 ID
   * @param correlationData 关联数据
   * @return 当前构建器
   */
  public FileMetadataTestDataBuilder withBusinessContext(
      String serviceName,
      String businessType,
      String businessId,
      Map<String, Object> correlationData) {
    this.context = new BusinessContext(serviceName, businessType, businessId, correlationData);
    return this;
  }

  /**
   * 配置 MD5 和 SHA-256 校验和。
   *
   * @param md5Hash MD5 哈希值
   * @param sha256Hash SHA-256 哈希值
   * @return 当前构建器
   */
  public FileMetadataTestDataBuilder withChecksum(String md5Hash, String sha256Hash) {
    this.checksum = new FileChecksum(md5Hash, sha256Hash);
    return this;
  }

  /**
   * 配置存储桶和对象键。
   *
   * @param bucket 存储桶名称
   * @param objectKey 对象键
   * @return 当前构建器
   */
  public FileMetadataTestDataBuilder withStorageKey(String bucket, String objectKey) {
    this.storageKey = new StorageKey(bucket, objectKey);
    return this;
  }

  /**
   * 配置文件大小（字节）。
   *
   * @param bytes 文件字节数
   * @return 当前构建器
   */
  public FileMetadataTestDataBuilder withFileSize(long bytes) {
    this.fileSize = new FileSize(bytes);
    return this;
  }

  // ========== Build 方法 ==========

  /**
   * 构建新创建的 FileMetadata 实例（使用 create() 工厂方法）。
   *
   * <p>新创建的聚合根：
   *
   * <ul>
   *   <li>ID 为 null
   *   <li>状态自动设置为 ACTIVE
   *   <li>上传时间、创建时间、更新时间自动设置为当前时间
   *   <li>版本号为 0
   *   <li>deleted 标志为 false
   * </ul>
   *
   * @return FileMetadata 实例
   */
  public FileMetadata build() {
    FileMetadata metadata =
        FileMetadata.create(storageKey, fileSize, checksum, context, provider);

    // 配置可选字段（如果提供）
    if (contentType != null) {
      metadata.withContentType(contentType);
    }
    if (expiresAt != null) {
      metadata.withExpiresAt(expiresAt);
    }
    if (recordRemarks != null) {
      metadata.withRecordRemarks(recordRemarks);
    }
    if (ipAddress != null) {
      metadata.withIpAddress(ipAddress);
    }

    return metadata;
  }

  /**
   * 构建从持久化恢复的 FileMetadata 实例（使用 restore() 工厂方法）。
   *
   * <p>从持久化恢复的聚合根：
   *
   * <ul>
   *   <li>ID 不为 null（如果未设置，默认为 100L）
   *   <li>所有字段都使用构建器中设置的值
   *   <li>完全控制所有字段的状态
   * </ul>
   *
   * @return FileMetadata 实例
   */
  public FileMetadata buildRestored() {
    Long restoredId = (id != null) ? id : 100L; // 默认 ID
    return FileMetadata.restore(
        restoredId,
        storageKey,
        fileSize,
        contentType,
        checksum,
        context,
        provider,
        status,
        uploadedAt,
        expiresAt,
        deletedAt,
        recordRemarks,
        version,
        ipAddress,
        createdAt,
        createdBy,
        createdByName,
        updatedAt,
        updatedBy,
        updatedByName,
        deleted);
  }
}
