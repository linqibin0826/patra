package com.patra.starter.objectstorage;

import com.patra.starter.objectstorage.domain.DownloadResult;
import com.patra.starter.objectstorage.domain.ObjectInfo;
import com.patra.starter.objectstorage.domain.ObjectMetadata;
import com.patra.starter.objectstorage.domain.ObjectNotFoundException;
import com.patra.starter.objectstorage.domain.UploadResult;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;

/// 与对象存储提供者交互的统一操作接口。
///
/// 此接口抽象了不同对象存储供应商(MinIO、AWS S3 等), 提供一致的文件存储操作 API。
///
/// **支持的操作:**
///
/// - {@link #upload} - 上传对象到存储
/// - {@link #download} - 下载对象内容（流式）
/// - {@link #downloadToFile} - 下载对象到本地文件
/// - {@link #statObject} - 获取对象元数据
/// - {@link #exists} - 检查对象是否存在
///
/// @see ObjectStorageTemplate 默认实现,包含重试和指标收集
/// @see ObjectStorageProvider 提供者抽象接口
public interface ObjectStorageOperations {

  /// 上传内容并返回存储对象的元数据。
  ///
  /// @param bucket 存储桶名称,如果不存在将自动创建(MinIO)或抛出异常(S3)
  /// @param key 存储桶内的唯一对象键
  /// @param inputStream 要上传的内容流
  /// @param metadata 辅助元数据,如大小和内容类型
  /// @return 上传结果,包含存储键和 ETag
  UploadResult upload(String bucket, String key, InputStream inputStream, ObjectMetadata metadata);

  /// 下载对象内容（流式）。
  ///
  /// **资源管理:** 调用者必须负责关闭返回的 {@link DownloadResult}，推荐使用
  /// try-with-resources 语法。
  ///
  /// **使用示例:**
  ///
  /// ```java
  /// try (DownloadResult result = objectStorage.download("bucket", "key")) {
  ///     InputStream content = result.getContent();
  ///     // 处理内容...
  /// }
  /// ```
  ///
  /// @param bucket 存储桶名称
  /// @param key 对象键
  /// @return 下载结果,包含内容流和元数据
  /// @throws ObjectNotFoundException 如果对象不存在
  DownloadResult download(String bucket, String key);

  /// 下载对象到指定文件路径。
  ///
  /// 框架自动管理流资源，下载完成后自动关闭。如果目标文件已存在，将被覆盖。
  ///
  /// @param bucket 存储桶名称
  /// @param key 对象键
  /// @param targetPath 目标文件路径
  /// @return 目标文件路径（与输入相同）
  /// @throws ObjectNotFoundException 如果对象不存在
  Path downloadToFile(String bucket, String key, Path targetPath);

  /// 获取对象元数据（HEAD 请求）。
  ///
  /// 此方法不下载对象内容，仅获取元数据信息。如果对象不存在，返回
  /// {@link Optional#empty()}。
  ///
  /// @param bucket 存储桶名称
  /// @param key 对象键
  /// @return 对象元数据,如果对象不存在则返回空 Optional
  Optional<ObjectInfo> statObject(String bucket, String key);

  /// 检查对象是否存在（便捷方法）。
  ///
  /// 等价于 {@code statObject(bucket, key).isPresent()}。
  ///
  /// @param bucket 存储桶名称
  /// @param key 对象键
  /// @return 如果对象存在返回 true,否则返回 false
  default boolean exists(String bucket, String key) {
    return statObject(bucket, key).isPresent();
  }
}
