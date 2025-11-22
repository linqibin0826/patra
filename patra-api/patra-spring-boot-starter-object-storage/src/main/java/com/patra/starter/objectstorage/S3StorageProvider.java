package com.patra.starter.objectstorage;

import com.patra.starter.objectstorage.domain.InvalidUploadRequestException;
import com.patra.starter.objectstorage.domain.ObjectMetadata;
import com.patra.starter.objectstorage.domain.UploadFailedException;
import com.patra.starter.objectstorage.domain.UploadResult;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

/// AWS S3 对象存储提供者实现。
///
/// 此实现负责:
///
/// - 上传文件到 AWS S3 服务
///   - **不会自动创建存储桶** - 存储桶必须预先存在
///   - 严格的参数验证(存储桶名称、对象键、文件大小)
///
/// @see MinioStorageProvider MinIO 实现(支持自动创建存储桶)
@Slf4j
public class S3StorageProvider implements ObjectStorageProvider {

  /// 存储桶名称验证模式,遵循 S3 命名规则。
  ///
  /// **规则:** 3-63 个字符,仅小写字母/数字/点/连字符,必须以字母或数字开头和结尾。
  private static final Pattern BUCKET_NAME_PATTERN =
      Pattern.compile("^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$");

  /// 对象键的最大长度(S3 限制)。
  private static final int MAX_KEY_LENGTH = 1024;

  private final S3Client s3Client;
  private final long maxFileSize;

  /// 构造一个新的 S3 存储提供者。
  ///
  /// @param s3Client 配置好的 S3 客户端
  /// @param maxFileSize 允许的最大文件大小(字节)
  public S3StorageProvider(S3Client s3Client, long maxFileSize) {
    this.s3Client = s3Client;
    this.maxFileSize = maxFileSize;
  }

  @Override
  public ProviderType getProviderType() {
    return ProviderType.S3;
  }

  /// 上传对象到 AWS S3 存储。
  ///
  /// **重要区别:** 与 MinIO 不同,S3 不会自动创建存储桶。 在调用此方法之前,目标存储桶必须已存在,否则将抛出异常。
  ///
  /// **资源管理:** AWS SDK 会消费 `inputStream` 并在内部关闭它。 调用者不应在此方法返回后尝试重用或关闭流。
  ///
  /// @param bucket 存储桶名称(必须已存在)
  /// @param key 存储桶内的唯一对象键
  /// @param inputStream 要上传的内容流(将被 AWS SDK 关闭)
  /// @param metadata 文件元数据,包括大小和内容类型
  /// @return 上传结果,包含存储键和 ETag
  /// @throws InvalidUploadRequestException 如果参数无效(不可重试)
  /// @throws UploadFailedException 如果上传因网络或服务器错误失败(可重试)
  @Override
  public UploadResult upload(
      String bucket, String key, InputStream inputStream, ObjectMetadata metadata) {
    try {
      validateArguments(bucket, key, inputStream, metadata);

      Map<String, String> userMetadata =
          metadata.getUserMetadata() == null
              ? Collections.emptyMap()
              : new HashMap<>(metadata.getUserMetadata());

      PutObjectRequest request =
          PutObjectRequest.builder()
              .bucket(bucket)
              .key(key)
              .contentType(metadata.getContentType())
              .metadata(userMetadata)
              .build();

      PutObjectResponse response =
          s3Client.putObject(
              request, RequestBody.fromInputStream(inputStream, metadata.getContentLength()));

      UploadResult result =
          UploadResult.builder()
              .storageKey(bucket + "/" + key)
              .bucketName(bucket)
              .objectKey(key)
              .etag(response.eTag())
              .fileSize(metadata.getContentLength())
              .build();

      log.info(
          "文件已成功上传到 S3: bucket={}, key={}, size={} 字节, etag={}",
          bucket,
          key,
          metadata.getContentLength(),
          response.eTag());

      return result;

    } catch (InvalidUploadRequestException ex) {
      // 不包装地重新抛出验证错误(不应重试)
      throw ex;
    } catch (Exception ex) {
      log.error(
          "S3 上传失败: bucket={}, key={}, size={} 字节", bucket, key, metadata.getContentLength(), ex);
      throw new UploadFailedException(String.format("S3 上传失败: bucket=%s, key=%s", bucket, key), ex);
    }
  }

  /// 验证上传参数的 null/空检查、格式合规性和大小限制。
  ///
  /// @param bucket 存储桶名称
  /// @param key 对象键
  /// @param inputStream 内容流
  /// @param metadata 文件元数据
  /// @throws InvalidUploadRequestException 如果任何验证失败
  private void validateArguments(
      String bucket, String key, InputStream inputStream, ObjectMetadata metadata) {
    // Null/空检查
    if (bucket == null || bucket.isBlank()) {
      throw new InvalidUploadRequestException("存储桶名称不能为空");
    }
    if (key == null || key.isBlank()) {
      throw new InvalidUploadRequestException("对象键不能为空");
    }
    if (inputStream == null) {
      throw new InvalidUploadRequestException("输入流不能为 null");
    }
    if (metadata == null) {
      throw new InvalidUploadRequestException("对象元数据是必需的");
    }
    if (metadata.getContentLength() <= 0) {
      throw new InvalidUploadRequestException(
          String.format("内容长度必须大于 0,实际为 %d", metadata.getContentLength()));
    }

    // 存储桶格式验证
    if (bucket.length() < 3 || bucket.length() > 63) {
      throw new InvalidUploadRequestException(
          String.format("存储桶名称长度必须在 3 到 63 个字符之间,实际为 %d", bucket.length()));
    }
    if (!BUCKET_NAME_PATTERN.matcher(bucket).matches()) {
      throw new InvalidUploadRequestException(
          String.format("存储桶名称 '%s' 无效。必须仅包含小写字母、数字、点和连字符,且必须以字母或数字开头和结尾", bucket));
    }
    if (bucket.contains("..")) {
      throw new InvalidUploadRequestException(String.format("存储桶名称 '%s' 包含连续的点,这是不允许的", bucket));
    }

    // 对象键格式验证
    if (key.length() > MAX_KEY_LENGTH) {
      throw new InvalidUploadRequestException(
          String.format("对象键长度超过最大值 %d 个字符,实际为 %d", MAX_KEY_LENGTH, key.length()));
    }
    if (key.startsWith("/")) {
      throw new InvalidUploadRequestException(String.format("对象键 '%s' 不能以斜杠开头", key));
    }
    if (key.contains("//")) {
      throw new InvalidUploadRequestException(String.format("对象键 '%s' 包含连续的斜杠,这是不允许的", key));
    }

    // 文件大小验证
    if (metadata.getContentLength() > maxFileSize) {
      throw new InvalidUploadRequestException(
          String.format(
              "文件大小 %d 字节超过了最大允许大小 %d 字节 (%.2f MB)",
              metadata.getContentLength(), maxFileSize, maxFileSize / 1024.0 / 1024.0));
    }
  }
}
