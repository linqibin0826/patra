package dev.linqibin.patra.objectstorage.app.recordupload;

import cn.hutool.core.text.CharSequenceUtil;
import dev.linqibin.commons.cqrs.Command;
import java.time.Instant;
import java.util.Map;

/// 记录上传命令。
///
/// 命令对象，描述需要记录的成功上传操作。封装了文件的存储位置、大小、校验和、
/// 业务上下文等所有必需信息，用于在应用层创建文件元数据记录。
///
/// **字段语义与约束**：
///
/// 必填字段：
/// - **bucketName**：存储桶名称，不能为空白
/// - **objectKey**：对象键，不能为空白
/// - **fileSize**：文件大小（字节），必须大于 0
/// - **md5Hash**：MD5 哈希值，不能为空白
/// - **serviceName**：服务名称，不能为空白
/// - **businessType**：业务类型，不能为空白
/// - **businessId**：业务 ID，不能为空白
/// - **providerType**：存储提供商类型，不能为空白
///
/// 可选字段：
/// - **contentType**：MIME 内容类型，可为 null
/// - **sha256Hash**：SHA-256 哈希值，可为 null
/// - **correlationData**：关联数据，为 null 时使用空 Map
/// - **expiresAt**：过期时间，可为 null
/// - **ipAddress**：IP 地址，可为 null
/// - **recordRemarks**：记录备注，可为 null
///
/// **不变量**：
///
/// - 必填字段不为 null 且不为空白（在 compact constructor 中校验）
/// - correlationData 和 ipAddress 进行防御性复制确保不可变性
///
/// **线程安全**：
///
/// Record 是不可变的，可安全跨线程共享。
///
/// @param bucketName 存储桶名称（必填）
/// @param objectKey 对象键（必填）
/// @param fileSize 文件大小（字节，必填，必须大于 0）
/// @param contentType MIME 内容类型（可选）
/// @param md5Hash MD5 哈希值（必填）
/// @param sha256Hash SHA-256 哈希值（可选）
/// @param serviceName 服务名称（必填）
/// @param businessType 业务类型（必填）
/// @param businessId 业务 ID（必填）
/// @param correlationData 关联数据（可选，为 null 时使用空 Map）
/// @param providerType 存储提供商类型（必填）
/// @param expiresAt 过期时间（可选）
/// @param ipAddress IP 地址（可选）
/// @param recordRemarks 记录备注（可选）
/// @author linqibin
/// @since 0.1.0
public record RecordUploadCommand(
    String bucketName,
    String objectKey,
    long fileSize,
    String contentType,
    String md5Hash,
    String sha256Hash,
    String serviceName,
    String businessType,
    String businessId,
    Map<String, Object> correlationData,
    String providerType,
    Instant expiresAt,
    byte[] ipAddress,
    String recordRemarks)
    implements Command<RecordUploadResult> {

  /// 构造并验证命令参数。
  ///
  /// @throws IllegalArgumentException 当必填字段为空或无效时
  public RecordUploadCommand {
    // 必填字段验证
    if (CharSequenceUtil.isBlank(bucketName)) {
      throw new IllegalArgumentException("bucketName 不能为空");
    }
    if (CharSequenceUtil.isBlank(objectKey)) {
      throw new IllegalArgumentException("objectKey 不能为空");
    }
    if (fileSize <= 0) {
      throw new IllegalArgumentException("fileSize 必须大于 0");
    }
    if (CharSequenceUtil.isBlank(md5Hash)) {
      throw new IllegalArgumentException("md5Hash 不能为空");
    }
    if (CharSequenceUtil.isBlank(serviceName)) {
      throw new IllegalArgumentException("serviceName 不能为空");
    }
    if (CharSequenceUtil.isBlank(businessType)) {
      throw new IllegalArgumentException("businessType 不能为空");
    }
    if (CharSequenceUtil.isBlank(businessId)) {
      throw new IllegalArgumentException("businessId 不能为空");
    }
    if (CharSequenceUtil.isBlank(providerType)) {
      throw new IllegalArgumentException("providerType 不能为空");
    }

    // 防御性复制
    correlationData = correlationData == null ? Map.of() : Map.copyOf(correlationData);
    ipAddress = ipAddress == null ? null : ipAddress.clone();
  }

  /// 创建命令构建器。
  ///
  /// @return 命令构建器
  public static Builder builder() {
    return new Builder();
  }

  /// RecordUploadCommand 构建器。
  ///
  /// 提供流式 API 构建命令对象，简化多参数命令的创建。
  public static class Builder {
    private String bucketName;
    private String objectKey;
    private long fileSize;
    private String contentType;
    private String md5Hash;
    private String sha256Hash;
    private String serviceName;
    private String businessType;
    private String businessId;
    private Map<String, Object> correlationData;
    private String providerType;
    private Instant expiresAt;
    private byte[] ipAddress;
    private String recordRemarks;

    /// 设置存储桶名称。
    public Builder bucketName(String bucketName) {
      this.bucketName = bucketName;
      return this;
    }

    /// 设置对象键。
    public Builder objectKey(String objectKey) {
      this.objectKey = objectKey;
      return this;
    }

    /// 设置文件大小。
    public Builder fileSize(long fileSize) {
      this.fileSize = fileSize;
      return this;
    }

    /// 设置内容类型。
    public Builder contentType(String contentType) {
      this.contentType = contentType;
      return this;
    }

    /// 设置 MD5 哈希值。
    public Builder md5Hash(String md5Hash) {
      this.md5Hash = md5Hash;
      return this;
    }

    /// 设置 SHA-256 哈希值。
    public Builder sha256Hash(String sha256Hash) {
      this.sha256Hash = sha256Hash;
      return this;
    }

    /// 设置服务名称。
    public Builder serviceName(String serviceName) {
      this.serviceName = serviceName;
      return this;
    }

    /// 设置业务类型。
    public Builder businessType(String businessType) {
      this.businessType = businessType;
      return this;
    }

    /// 设置业务 ID。
    public Builder businessId(String businessId) {
      this.businessId = businessId;
      return this;
    }

    /// 设置关联数据。
    public Builder correlationData(Map<String, Object> correlationData) {
      this.correlationData = correlationData;
      return this;
    }

    /// 设置存储提供商类型。
    public Builder providerType(String providerType) {
      this.providerType = providerType;
      return this;
    }

    /// 设置过期时间。
    public Builder expiresAt(Instant expiresAt) {
      this.expiresAt = expiresAt;
      return this;
    }

    /// 设置 IP 地址。
    public Builder ipAddress(byte[] ipAddress) {
      this.ipAddress = ipAddress;
      return this;
    }

    /// 设置记录备注。
    public Builder recordRemarks(String recordRemarks) {
      this.recordRemarks = recordRemarks;
      return this;
    }

    /// 构建命令对象。
    ///
    /// @return 构建的命令对象
    /// @throws IllegalArgumentException 当必填字段为空或无效时
    public RecordUploadCommand build() {
      return new RecordUploadCommand(
          bucketName,
          objectKey,
          fileSize,
          contentType,
          md5Hash,
          sha256Hash,
          serviceName,
          businessType,
          businessId,
          correlationData,
          providerType,
          expiresAt,
          ipAddress,
          recordRemarks);
    }
  }
}
