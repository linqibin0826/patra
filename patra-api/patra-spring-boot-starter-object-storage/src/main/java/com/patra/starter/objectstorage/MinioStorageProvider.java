package com.patra.starter.objectstorage;

import com.patra.starter.objectstorage.domain.InvalidUploadRequestException;
import com.patra.starter.objectstorage.domain.ObjectMetadata;
import com.patra.starter.objectstorage.domain.UploadFailedException;
import com.patra.starter.objectstorage.domain.UploadResult;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/// MinIO 对象存储提供者实现。
///
/// 此实现负责:
///
/// - 上传文件到 MinIO 服务器
///   - 自动创建不存在的存储桶(lazy bucket creation)
///   - 缓存已知存储桶,避免重复的存在性检查
///   - 严格的参数验证(存储桶名称、对象键、文件大小)
///
/// @see S3StorageProvider AWS S3 实现
@Slf4j
public class MinioStorageProvider implements ObjectStorageProvider {

  /// 存储桶名称验证模式,遵循 S3 命名规则。
  ///
  /// **规则:** 3-63 个字符,仅小写字母/数字/点/连字符,必须以字母或数字开头和结尾。
  private static final Pattern BUCKET_NAME_PATTERN =
      Pattern.compile("^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$");

  /// 对象键的最大长度(S3 兼容性限制)。
  private static final int MAX_KEY_LENGTH = 1024;

  private final MinioClient minioClient;
  private final long maxFileSize;

  /// 已知存储桶的本地缓存,避免冗余的存在性检查。
  ///
  /// 此缓存通过跳过对 {@link MinioClient#bucketExists} 的网络调用, 提高了向同一存储桶进行高频上传的性能。如果存储桶在外部被删除,
  /// 缓存将变为陈旧,但后续上传尝试将优雅地失败并提供清晰的错误消息。
  private final Set<String> knownBuckets = ConcurrentHashMap.newKeySet();

  /// 构造一个新的 MinIO 存储提供者。
  ///
  /// @param minioClient 配置好的 MinIO 客户端
  /// @param maxFileSize 允许的最大文件大小(字节)
  public MinioStorageProvider(MinioClient minioClient, long maxFileSize) {
    this.minioClient = minioClient;
    this.maxFileSize = maxFileSize;
  }

  @Override
  public ProviderType getProviderType() {
    return ProviderType.MINIO;
  }

  /// 上传对象到 MinIO 存储。
  ///
  /// 此方法在上传前确保目标存储桶存在。如果存储桶不存在,将自动创建。
  ///
  /// **资源管理:** MinIO SDK 会消费 `inputStream` 并在内部关闭它。 调用者不应在此方法返回后尝试重用或关闭流。
  ///
  /// @param bucket 存储桶名称(如果不存在将自动创建)
  /// @param key 存储桶内的唯一对象键
  /// @param inputStream 要上传的内容流(将被 MinIO SDK 关闭)
  /// @param metadata 文件元数据,包括大小和内容类型
  /// @return 上传结果,包含存储键和 ETag
  /// @throws InvalidUploadRequestException 如果参数无效(不可重试)
  /// @throws UploadFailedException 如果上传因网络或服务器错误失败(可重试)
  @Override
  public UploadResult upload(
      String bucket, String key, InputStream inputStream, ObjectMetadata metadata) {
    try {
      validateArguments(bucket, key, inputStream, metadata);
      ensureBucketExists(bucket);

      ObjectWriteResponse response =
          minioClient.putObject(
              PutObjectArgs.builder().bucket(bucket).object(key).stream(
                      inputStream, metadata.getContentLength(), -1)
                  .contentType(metadata.getContentType())
                  .build());

      UploadResult result =
          UploadResult.builder()
              .storageKey(bucket + "/" + key)
              .bucketName(bucket)
              .objectKey(key)
              .etag(response.etag())
              .fileSize(metadata.getContentLength())
              .build();

      log.info(
          "文件已成功上传到 MinIO: bucket={}, key={}, size={} 字节, etag={}",
          bucket,
          key,
          metadata.getContentLength(),
          response.etag());

      return result;

    } catch (InvalidUploadRequestException ex) {
      // 不包装地重新抛出验证错误(不应重试)
      throw ex;
    } catch (Exception ex) {
      log.error(
          "MinIO 上传失败: bucket={}, key={}, size={} 字节",
          bucket,
          key,
          metadata.getContentLength(),
          ex);
      throw new UploadFailedException(
          String.format("MinIO 上传失败: bucket=%s, key=%s", bucket, key), ex);
    }
  }

  /// 确保指定的存储桶存在,必要时创建它。
  ///
  /// 此方法使用本地缓存避免对频繁访问的存储桶进行冗余网络调用。 首次访问时,它会检查 MinIO 并缓存结果。
  ///
  /// @param bucket 要检查/创建的存储桶名称
  /// @throws Exception 如果存储桶检查或创建失败
  private void ensureBucketExists(String bucket) throws Exception {
    // 首先检查缓存以避免网络调用
    if (knownBuckets.contains(bucket)) {
      return;
    }

    // 缓存未命中 - 检查 MinIO
    boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
    if (!exists) {
      minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
      log.info("已创建 MinIO 存储桶: {}", bucket);
    }

    // 为未来的上传添加到缓存
    knownBuckets.add(bucket);
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
