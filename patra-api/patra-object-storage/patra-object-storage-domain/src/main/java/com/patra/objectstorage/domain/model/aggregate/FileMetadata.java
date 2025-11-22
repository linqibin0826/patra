package com.patra.objectstorage.domain.model.aggregate;

import com.patra.objectstorage.domain.model.enums.FileStatus;
import com.patra.objectstorage.domain.model.enums.StorageProvider;
import com.patra.objectstorage.domain.model.vo.BusinessContext;
import com.patra.objectstorage.domain.model.vo.FileChecksum;
import com.patra.objectstorage.domain.model.vo.FileSize;
import com.patra.objectstorage.domain.model.vo.StorageKey;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;

/// 文件元数据聚合根。
///
/// 表示存储在对象存储系统中的每个文件的元数据记录。作为聚合根,它封装了文件的完整生命周期信息, 包括存储位置、大小、校验和、业务上下文、状态追踪和审计信息。
///
/// 聚合根通过工厂方法创建新实例({@link #create})或从持久化快照恢复({@link #restore}), 确保所有必需的业务规则在对象创建时得到验证。
@Getter
@ToString
public class FileMetadata {

  /// 主键标识符
  private Long id;

  /// 存储键(bucket/objectKey)
  private StorageKey storageKey;

  /// 文件大小
  private FileSize fileSize;

  /// MIME 内容类型
  private String contentType;

  /// 文件校验和(MD5/SHA-256)
  private FileChecksum checksum;

  /// 业务上下文
  private BusinessContext context;

  /// 存储提供商
  private StorageProvider provider;

  /// 文件状态
  private FileStatus status;

  /// 上传完成时间
  private Instant uploadedAt;

  /// 过期时间
  private Instant expiresAt;

  /// 删除时间
  private Instant deletedAt;

  /// 记录备注(JSON格式)
  private String recordRemarks;

  /// 乐观锁版本号
  private Long version;

  /// 请求者IP地址(二进制格式)
  @Getter(AccessLevel.NONE)
  private byte[] ipAddress;

  /// 创建时间
  private Instant createdAt;

  /// 创建人ID
  private Long createdBy;

  /// 创建人姓名
  private String createdByName;

  /// 更新时间
  private Instant updatedAt;

  /// 更新人ID
  private Long updatedBy;

  /// 更新人姓名
  private String updatedByName;

  /// 软删除标志
  private Boolean deleted;

  /// 私有构造函数,强制使用工厂方法。
  private FileMetadata() {
    // 使用静态工厂方法创建实例
  }

  /// 创建新的元数据聚合根,初始化必需的审计属性。
  ///
  /// 工厂方法用于创建新的文件元数据记录,自动设置初始状态为ACTIVE, 初始化上传时间、版本号和审计时间戳。
  ///
  /// @param storageKey 规范的存储定位符
  /// @param fileSize 物理载荷大小
  /// @param checksum 完整性校验和数据
  /// @param context 上游业务上下文
  /// @param provider 存储提供商类型
  /// @return 初始化完成的聚合根,可用于持久化
  public static FileMetadata create(
      StorageKey storageKey,
      FileSize fileSize,
      FileChecksum checksum,
      BusinessContext context,
      StorageProvider provider) {
    Objects.requireNonNull(storageKey, "storageKey 不能为 null");
    Objects.requireNonNull(fileSize, "fileSize 不能为 null");
    Objects.requireNonNull(checksum, "checksum 不能为 null");
    Objects.requireNonNull(context, "context 不能为 null");
    Objects.requireNonNull(provider, "provider 不能为 null");

    FileMetadata metadata = new FileMetadata();
    metadata.storageKey = storageKey;
    metadata.fileSize = fileSize;
    metadata.checksum = checksum;
    metadata.context = context;
    metadata.provider = provider;
    metadata.status = FileStatus.ACTIVE;
    metadata.uploadedAt = Instant.now();
    metadata.version = 0L;
    metadata.deleted = Boolean.FALSE;
    metadata.createdAt = metadata.uploadedAt;
    metadata.updatedAt = metadata.uploadedAt;
    return metadata;
  }

  /// 从现有的持久化快照恢复聚合根。
  ///
  /// 工厂方法用于从数据库加载已存在的元数据记录,重建完整的聚合根状态。 此方法用于仓储实现将数据实体转换为领域对象。
  ///
  /// @param id 主键
  /// @param storageKey 持久化的存储键
  /// @param fileSize 持久化的字节大小
  /// @param contentType 持久化的MIME类型
  /// @param checksum 校验和快照
  /// @param context 业务上下文快照
  /// @param provider 提供商类型
  /// @param status 文件生命周期状态
  /// @param uploadedAt 上传时间戳
  /// @param expiresAt 过期时间戳
  /// @param deletedAt 删除时间戳
  /// @param recordRemarks 审计备注载荷
  /// @param version 乐观锁版本
  /// @param ipAddress 以二进制存储的请求者IP
  /// @param createdAt 创建时间
  /// @param createdBy 创建人ID
  /// @param createdByName 创建人显示名称
  /// @param updatedAt 最后更新时间
  /// @param updatedBy 更新人ID
  /// @param updatedByName 更新人显示名称
  /// @param deleted 逻辑删除标记
  /// @return 完全物化的聚合根
  public static FileMetadata restore(
      Long id,
      StorageKey storageKey,
      FileSize fileSize,
      String contentType,
      FileChecksum checksum,
      BusinessContext context,
      StorageProvider provider,
      FileStatus status,
      Instant uploadedAt,
      Instant expiresAt,
      Instant deletedAt,
      String recordRemarks,
      Long version,
      byte[] ipAddress,
      Instant createdAt,
      Long createdBy,
      String createdByName,
      Instant updatedAt,
      Long updatedBy,
      String updatedByName,
      Boolean deleted) {
    FileMetadata metadata = new FileMetadata();
    metadata.id = id;
    metadata.storageKey = storageKey;
    metadata.fileSize = fileSize;
    metadata.contentType = contentType;
    metadata.checksum = checksum;
    metadata.context = context;
    metadata.provider = provider;
    metadata.status = status;
    metadata.uploadedAt = uploadedAt;
    metadata.expiresAt = expiresAt;
    metadata.deletedAt = deletedAt;
    metadata.recordRemarks = recordRemarks;
    metadata.version = version;
    metadata.ipAddress = ipAddress == null ? null : ipAddress.clone();
    metadata.createdAt = createdAt;
    metadata.createdBy = createdBy;
    metadata.createdByName = createdByName;
    metadata.updatedAt = updatedAt;
    metadata.updatedBy = updatedBy;
    metadata.updatedByName = updatedByName;
    metadata.deleted = deleted;
    return metadata;
  }

  /// 在持久化后分配生成的标识符。
  ///
  /// 仓储实现在保存新记录后调用此方法,将数据库生成的主键ID回填到聚合根。
  ///
  /// @param id 数据库主键
  /// @throws IllegalStateException 如果聚合根已有ID
  public void assignId(Long id) {
    if (this.id != null) {
      throw new IllegalStateException("聚合根已经有ID");
    }
    this.id = Objects.requireNonNull(id, "id 不能为 null");
  }

  /// 更新乐观锁版本,仓储在保存时调用。
  ///
  /// 用于支持乐观锁并发控制,防止丢失更新问题。
  ///
  /// @param version 新的版本值
  public void updateVersion(Long version) {
    this.version = Objects.requireNonNull(version, "version 不能为 null");
  }

  /// 配置与存储对象关联的MIME类型。
  ///
  /// @param contentType 可选的MIME类型字符串
  /// @return 当前聚合根,用于链式调用
  public FileMetadata withContentType(String contentType) {
    this.contentType = contentType;
    return this;
  }

  /// 配置用于保留期管理的过期时间。
  ///
  /// @param expiresAt 可选的过期时间戳
  /// @return 当前聚合根,用于链式调用
  public FileMetadata withExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
    return this;
  }

  /// 捕获调用方提供的备注,用于审计追踪。
  ///
  /// @param remarks 可选的JSON/文本备注
  /// @return 当前聚合根,用于链式调用
  public FileMetadata withRecordRemarks(String remarks) {
    this.recordRemarks = remarks;
    return this;
  }

  /// 以二进制形式存储调用方IP(IPv4/IPv6)。
  ///
  /// @param ip 可选的二进制IP表示
  /// @return 当前聚合根,用于链式调用
  public FileMetadata withIpAddress(byte[] ip) {
    this.ipAddress = ip == null ? null : ip.clone();
    return this;
  }

  /// 将文件标记为已删除,同时保留审计追踪。
  ///
  /// 软删除操作,将状态更新为DELETED,设置删除标志和删除时间,并更新审计信息。
  ///
  /// @param operatorId 执行删除的操作员标识符
  /// @param operatorName 操作员显示名称
  /// @throws IllegalStateException 如果文件已被删除
  public void markAsDeleted(Long operatorId, String operatorName) {
    if (this.status == FileStatus.DELETED) {
      throw new IllegalStateException("文件已被删除");
    }
    this.status = FileStatus.DELETED;
    this.deleted = Boolean.TRUE;
    this.deletedAt = Instant.now();
    touchAudit(operatorId, operatorName);
  }

  /// 检查元数据是否已超过保留期限。
  ///
  /// @return 当文件应被视为已过期时返回 `true`
  public boolean isExpired() {
    return expiresAt != null && Instant.now().isAfter(expiresAt);
  }

  /// 返回存储的IP地址的防御性副本。
  ///
  /// @return 二进制IP,如果未提供则返回 `null`
  public byte[] getIpAddress() {
    return ipAddress == null ? null : ipAddress.clone();
  }

  /// 刷新审计元数据。
  ///
  /// 更新最后修改时间和修改人信息,用于追踪聚合根的变更历史。
  ///
  /// @param operatorId 可选的操作员ID
  /// @param operatorName 可选的操作员显示名称
  public void touchAudit(Long operatorId, String operatorName) {
    this.updatedAt = Instant.now();
    this.updatedBy = operatorId;
    this.updatedByName = operatorName;
  }
}
