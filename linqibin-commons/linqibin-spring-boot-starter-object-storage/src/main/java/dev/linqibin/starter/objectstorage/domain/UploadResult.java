package dev.linqibin.starter.objectstorage.domain;

import lombok.Builder;
import lombok.Getter;

/// 上传操作的结果。
@Getter
@Builder
public class UploadResult {
  /// 存储键
  private final String storageKey;

  /// 存储桶名称
  private final String bucketName;

  /// 对象键
  private final String objectKey;

  /// ETag 标识
  private final String etag;

  /// 文件大小(字节)
  private final long fileSize;
}
