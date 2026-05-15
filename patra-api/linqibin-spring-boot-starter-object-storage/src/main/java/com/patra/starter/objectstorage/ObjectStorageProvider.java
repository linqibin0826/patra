package com.patra.starter.objectstorage;

import com.patra.starter.objectstorage.domain.DownloadResult;
import com.patra.starter.objectstorage.domain.ObjectInfo;
import com.patra.starter.objectstorage.domain.ObjectMetadata;
import com.patra.starter.objectstorage.domain.UploadResult;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;

/// 各个对象存储供应商的策略契约。
public interface ObjectStorageProvider {

  /// 获取提供商标识符,用于路由和可观测性标签。
  ///
  /// @return 提供商类型
  ProviderType getProviderType();

  /// 通过具体的提供商 API 执行上传操作。
  ///
  /// @param bucket 存储桶名称
  /// @param key 对象键
  /// @param inputStream 输入流
  /// @param metadata 对象元数据
  /// @return 上传结果
  UploadResult upload(String bucket, String key, InputStream inputStream, ObjectMetadata metadata);

  /// 下载对象内容（流式）。
  ///
  /// @param bucket 存储桶名称
  /// @param key 对象键
  /// @return 下载结果
  DownloadResult download(String bucket, String key);

  /// 下载对象到指定文件路径。
  ///
  /// @param bucket 存储桶名称
  /// @param key 对象键
  /// @param targetPath 目标文件路径
  /// @return 目标文件路径
  Path downloadToFile(String bucket, String key, Path targetPath);

  /// 获取对象元数据（HEAD 请求）。
  ///
  /// @param bucket 存储桶名称
  /// @param key 对象键
  /// @return 对象元数据,如果对象不存在则返回空 Optional
  Optional<ObjectInfo> statObject(String bucket, String key);
}
