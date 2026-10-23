package dev.patra.tool.objectstorage.provider;

import dev.patra.tool.objectstorage.domain.DeleteFailedException;
import dev.patra.tool.objectstorage.domain.DownloadFailedException;
import dev.patra.tool.objectstorage.domain.DownloadResult;
import dev.patra.tool.objectstorage.domain.InvalidDownloadRequestException;
import dev.patra.tool.objectstorage.domain.InvalidUploadRequestException;
import dev.patra.tool.objectstorage.domain.ObjectInfo;
import dev.patra.tool.objectstorage.domain.ObjectMetadata;
import dev.patra.tool.objectstorage.domain.ObjectNotFoundException;
import dev.patra.tool.objectstorage.domain.UploadFailedException;
import dev.patra.tool.objectstorage.domain.UploadResult;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
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
public class S3StorageProvider extends AbstractObjectStorageProvider {

  /// 每批最多删除几个对象（S3 协议上限）。
  private static final int DELETE_BATCH_SIZE = 1000;

  private final S3Client s3Client;

  /// 构造一个新的 S3 存储提供者。
  ///
  /// @param s3Client 配置好的 S3 客户端
  /// @param maxFileSize 允许的最大文件大小(字节)
  public S3StorageProvider(S3Client s3Client, long maxFileSize) {
    super(maxFileSize);
    this.s3Client = s3Client;
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
      validateUploadArguments(bucket, key, inputStream, metadata);

      Map<String, String> userMetadata =
          metadata.getUserMetadata() == null ? Map.of() : new HashMap<>(metadata.getUserMetadata());

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

      GetObjectRequest request = GetObjectRequest.builder().bucket(bucket).key(key).build();

      ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request);
      GetObjectResponse objectResponse = response.response();

      DownloadResult result =
          DownloadResult.builder()
              .content(response)
              .bucketName(bucket)
              .objectKey(key)
              .contentLength(objectResponse.contentLength())
              .contentType(objectResponse.contentType())
              .etag(objectResponse.eTag())
              .build();

      log.info(
          "开始从 S3 下载对象: bucket={}, key={}, size={} 字节",
          bucket,
          key,
          objectResponse.contentLength());

      return result;

    } catch (InvalidDownloadRequestException ex) {
      throw ex;
    } catch (NoSuchKeyException ex) {
      throw new ObjectNotFoundException(bucket, key, ex);
    } catch (Exception ex) {
      log.error("S3 下载失败: bucket={}, key={}", bucket, key, ex);
      throw new DownloadFailedException(
          String.format("S3 下载失败: bucket=%s, key=%s", bucket, key), ex);
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

      GetObjectRequest request = GetObjectRequest.builder().bucket(bucket).key(key).build();

      s3Client.getObject(request, targetPath);

      log.info("文件已下载到: bucket={}, key={}, path={}", bucket, key, targetPath);

      return targetPath;

    } catch (InvalidDownloadRequestException ex) {
      throw ex;
    } catch (NoSuchKeyException ex) {
      throw new ObjectNotFoundException(bucket, key, ex);
    } catch (Exception ex) {
      log.error("S3 下载到文件失败: bucket={}, key={}, path={}", bucket, key, targetPath, ex);
      throw new DownloadFailedException(
          String.format("S3 下载到文件失败: bucket=%s, key=%s, path=%s", bucket, key, targetPath), ex);
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

      HeadObjectRequest request = HeadObjectRequest.builder().bucket(bucket).key(key).build();

      HeadObjectResponse response = s3Client.headObject(request);

      ObjectInfo info =
          ObjectInfo.builder()
              .bucketName(bucket)
              .objectKey(key)
              .contentLength(response.contentLength())
              .contentType(response.contentType())
              .etag(response.eTag())
              .lastModified(response.lastModified())
              .build();

      return Optional.of(info);

    } catch (InvalidDownloadRequestException ex) {
      throw ex;
    } catch (NoSuchKeyException ex) {
      return Optional.empty();
    } catch (Exception ex) {
      log.error("S3 获取对象信息失败: bucket={}, key={}", bucket, key, ex);
      throw new DownloadFailedException(
          String.format("S3 获取对象信息失败: bucket=%s, key=%s", bucket, key), ex);
    }
  }

  /// 删除对象；S3 的删除本来就幂等。
  ///
  /// @param bucket 存储桶名称
  /// @param key 对象键
  /// @throws DeleteFailedException 删除失败
  @Override
  public void delete(String bucket, String key) {
    validateDownloadArguments(bucket, key);
    try {
      s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    } catch (NoSuchBucketException ex) {
      // 桶都不在，对象自然不在
    } catch (Exception ex) {
      throw new DeleteFailedException(
          String.format("S3 删除对象失败: bucket=%s, key=%s", bucket, key), ex);
    }
  }

  /// 分页列出前缀下的键，按每批最多 1000 个删除。
  ///
  /// @param bucket 存储桶名称
  /// @param prefix 键前缀
  /// @return 删除的对象个数；桶不存在时为 0
  /// @throws DeleteFailedException 列举或删除失败
  @Override
  public int deleteByPrefix(String bucket, String prefix) {
    validatePrefix(bucket, prefix);
    List<ObjectIdentifier> keys = new ArrayList<>();
    try {
      s3Client
          .listObjectsV2Paginator(
              ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).build())
          .contents()
          .forEach(object -> keys.add(ObjectIdentifier.builder().key(object.key()).build()));
    } catch (NoSuchBucketException ex) {
      return 0;
    } catch (Exception ex) {
      throw new DeleteFailedException(
          String.format("S3 列举对象失败: bucket=%s, prefix=%s", bucket, prefix), ex);
    }
    for (int from = 0; from < keys.size(); from += DELETE_BATCH_SIZE) {
      List<ObjectIdentifier> batch =
          keys.subList(from, Math.min(keys.size(), from + DELETE_BATCH_SIZE));
      DeleteObjectsResponse response;
      try {
        response =
            s3Client.deleteObjects(
                DeleteObjectsRequest.builder()
                    .bucket(bucket)
                    .delete(Delete.builder().objects(batch).build())
                    .build());
      } catch (Exception ex) {
        throw new DeleteFailedException(
            String.format("S3 批量删除失败: bucket=%s, prefix=%s", bucket, prefix), ex);
      }
      if (response.hasErrors() && !response.errors().isEmpty()) {
        throw new DeleteFailedException(
            String.format(
                "S3 批量删除有 %d 个对象失败: bucket=%s, prefix=%s, 例如 %s",
                response.errors().size(), bucket, prefix, response.errors().getFirst().key()));
      }
    }
    return keys.size();
  }
}
