package com.patra.starter.objectstorage;

import com.patra.starter.objectstorage.domain.ObjectMetadata;
import com.patra.starter.objectstorage.domain.UploadResult;
import java.io.InputStream;

/// 与对象存储提供者交互的统一操作接口。
/// 
/// 此接口抽象了不同对象存储供应商(MinIO、AWS S3 等), 提供一致的文件存储操作 API。
/// 
/// **当前实现范围(第一阶段):**
/// 
/// - ✅ {@link #upload} - 上传对象到存储
///   - ⏳ 下载操作 - 计划在第二阶段实现
///   - ⏳ 删除操作 - 计划在第二阶段实现
///   - ⏳ 存在性检查 - 计划在第二阶段实现
///   - ⏳ 元数据检索 - 计划在第二阶段实现
/// 
/// 这种增量方法允许我们快速交付核心功能,同时收集其他操作的需求。
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
}
