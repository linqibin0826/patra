package dev.linqibin.starter.objectstorage;

import dev.linqibin.starter.objectstorage.domain.DownloadFailedException;
import dev.linqibin.starter.objectstorage.domain.DownloadResult;
import dev.linqibin.starter.objectstorage.domain.InvalidDownloadRequestException;
import dev.linqibin.starter.objectstorage.domain.InvalidUploadRequestException;
import dev.linqibin.starter.objectstorage.domain.ObjectInfo;
import dev.linqibin.starter.objectstorage.domain.ObjectMetadata;
import dev.linqibin.starter.objectstorage.domain.ObjectNotFoundException;
import dev.linqibin.starter.objectstorage.domain.UploadFailedException;
import dev.linqibin.starter.objectstorage.domain.UploadResult;
import io.minio.BucketExistsArgs;
import io.minio.DownloadObjectArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
public class MinioStorageProvider extends AbstractObjectStorageProvider {

  private final MinioClient minioClient;

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
    super(maxFileSize);
    this.minioClient = minioClient;
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
      validateUploadArguments(bucket, key, inputStream, metadata);
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

  /// 下载对象内容（流式）。
  ///
  /// @param bucket 存储桶名称
  /// @param key 对象键
  /// @return 下载结果,包含内容流和元数据
  /// @throws ObjectNotFoundException 如果对象不存在
  /// @throws InvalidDownloadRequestException 如果参数无效
  /// @throws DownloadFailedException 如果下载失败
  @Override
  public DownloadResult download(String bucket, String key) {
    try {
      validateDownloadArguments(bucket, key);

      GetObjectResponse response =
          minioClient.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build());

      // 获取对象信息
      StatObjectResponse stat =
          minioClient.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build());

      DownloadResult result =
          DownloadResult.builder()
              .content(response)
              .bucketName(bucket)
              .objectKey(key)
              .contentLength(stat.size())
              .contentType(stat.contentType())
              .etag(stat.etag())
              .build();

      log.info("开始从 MinIO 下载对象: bucket={}, key={}, size={} 字节", bucket, key, stat.size());

      return result;

    } catch (InvalidDownloadRequestException ex) {
      throw ex;
    } catch (ErrorResponseException ex) {
      if ("NoSuchKey".equals(ex.errorResponse().code())) {
        throw new ObjectNotFoundException(bucket, key, ex);
      }
      log.error("MinIO 下载失败: bucket={}, key={}", bucket, key, ex);
      throw new DownloadFailedException(
          String.format("MinIO 下载失败: bucket=%s, key=%s", bucket, key), ex);
    } catch (Exception ex) {
      log.error("MinIO 下载失败: bucket={}, key={}", bucket, key, ex);
      throw new DownloadFailedException(
          String.format("MinIO 下载失败: bucket=%s, key=%s", bucket, key), ex);
    }
  }

  /// 下载对象到指定文件路径。
  ///
  /// @param bucket 存储桶名称
  /// @param key 对象键
  /// @param targetPath 目标文件路径
  /// @return 目标文件路径
  /// @throws ObjectNotFoundException 如果对象不存在
  /// @throws InvalidDownloadRequestException 如果参数无效
  /// @throws DownloadFailedException 如果下载失败
  @Override
  public Path downloadToFile(String bucket, String key, Path targetPath) {
    try {
      validateDownloadArguments(bucket, key);
      if (targetPath == null) {
        throw new InvalidDownloadRequestException("目标文件路径不能为 null");
      }

      minioClient.downloadObject(
          DownloadObjectArgs.builder()
              .bucket(bucket)
              .object(key)
              .filename(targetPath.toString())
              .build());

      log.info("文件已下载到: bucket={}, key={}, path={}", bucket, key, targetPath);

      return targetPath;

    } catch (InvalidDownloadRequestException ex) {
      throw ex;
    } catch (ErrorResponseException ex) {
      if ("NoSuchKey".equals(ex.errorResponse().code())) {
        throw new ObjectNotFoundException(bucket, key, ex);
      }
      log.error("MinIO 下载到文件失败: bucket={}, key={}, path={}", bucket, key, targetPath, ex);
      throw new DownloadFailedException(
          String.format("MinIO 下载到文件失败: bucket=%s, key=%s, path=%s", bucket, key, targetPath), ex);
    } catch (Exception ex) {
      log.error("MinIO 下载到文件失败: bucket={}, key={}, path={}", bucket, key, targetPath, ex);
      throw new DownloadFailedException(
          String.format("MinIO 下载到文件失败: bucket=%s, key=%s, path=%s", bucket, key, targetPath), ex);
    }
  }

  /// 获取对象元数据（HEAD 请求）。
  ///
  /// @param bucket 存储桶名称
  /// @param key 对象键
  /// @return 对象元数据,如果对象不存在则返回空 Optional
  /// @throws InvalidDownloadRequestException 如果参数无效
  /// @throws DownloadFailedException 如果获取元数据失败（非对象不存在的错误）
  @Override
  public Optional<ObjectInfo> statObject(String bucket, String key) {
    try {
      validateDownloadArguments(bucket, key);

      StatObjectResponse stat =
          minioClient.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build());

      ObjectInfo info =
          ObjectInfo.builder()
              .bucketName(bucket)
              .objectKey(key)
              .contentLength(stat.size())
              .contentType(stat.contentType())
              .etag(stat.etag())
              .lastModified(stat.lastModified().toInstant())
              .build();

      return Optional.of(info);

    } catch (InvalidDownloadRequestException ex) {
      throw ex;
    } catch (ErrorResponseException ex) {
      if ("NoSuchKey".equals(ex.errorResponse().code())) {
        return Optional.empty();
      }
      log.error("MinIO 获取对象信息失败: bucket={}, key={}", bucket, key, ex);
      throw new DownloadFailedException(
          String.format("MinIO 获取对象信息失败: bucket=%s, key=%s", bucket, key), ex);
    } catch (Exception ex) {
      log.error("MinIO 获取对象信息失败: bucket={}, key={}", bucket, key, ex);
      throw new DownloadFailedException(
          String.format("MinIO 获取对象信息失败: bucket=%s, key=%s", bucket, key), ex);
    }
  }
}
