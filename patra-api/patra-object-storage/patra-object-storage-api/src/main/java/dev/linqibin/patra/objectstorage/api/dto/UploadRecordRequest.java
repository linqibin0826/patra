package dev.linqibin.patra.objectstorage.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;

/// 上传记录请求。
///
/// 调用方用于持久化上传元数据的请求体。包含文件的存储位置、大小、校验和、 业务上下文等所有必需信息,用于在存储服务中创建元数据记录。
///
/// 验证约束:
///
/// - bucketName、objectKey、md5Hash、serviceName、businessType、businessId、providerType 不能为空
///   - fileSize 必须 >= 0
///   - contentType 最大128字符
///   - recordRemarks 最大512字符
///
/// @param bucketName 存储桶名称
/// @param objectKey 对象键
/// @param fileSize 文件大小(字节)
/// @param contentType MIME内容类型
/// @param md5Hash MD5哈希值
/// @param sha256Hash SHA-256哈希值(可选)
/// @param serviceName 服务名称
/// @param businessType 业务类型
/// @param businessId 业务ID
/// @param correlationData 关联数据
/// @param providerType 存储提供商类型
/// @param expiresAt 过期时间(可选)
/// @param recordRemarks 记录备注(可选)
public record UploadRecordRequest(
    @NotBlank String bucketName,
    @NotBlank String objectKey,
    @PositiveOrZero long fileSize,
    @Size(max = 128) String contentType,
    @NotBlank String md5Hash,
    String sha256Hash,
    @NotBlank String serviceName,
    @NotBlank String businessType,
    @NotBlank String businessId,
    Map<String, Object> correlationData,
    @NotBlank String providerType,
    Instant expiresAt,
    @Size(max = 512) String recordRemarks) {

  /// Canonical constructor normalizing optional fields.
  public UploadRecordRequest {
    correlationData = correlationData == null ? Map.of() : Map.copyOf(correlationData);
  }

  /// @return canonical storage key in `bucket/objectKey` form.
  public String storageKey() {
    return bucketName + "/" + objectKey;
  }
}
