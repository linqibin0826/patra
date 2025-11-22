package com.patra.starter.objectstorage;

import com.patra.starter.objectstorage.domain.ObjectMetadata;
import com.patra.starter.objectstorage.domain.UploadResult;
import java.io.InputStream;

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
}
