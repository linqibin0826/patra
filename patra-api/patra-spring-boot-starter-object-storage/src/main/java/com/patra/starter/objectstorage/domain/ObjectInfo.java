package com.patra.starter.objectstorage.domain;

import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

/// 对象元数据信息。
///
/// 此类封装了从对象存储获取的对象元数据（通过 HEAD 请求），
/// 不包含对象内容本身。用于检查对象是否存在以及获取其属性。
@Getter
@Builder
public class ObjectInfo {

  /// 存储桶名称。
  private final String bucketName;

  /// 对象键。
  private final String objectKey;

  /// 内容长度（字节）。
  private final long contentLength;

  /// 内容类型（MIME 类型）。
  private final String contentType;

  /// 对象的 ETag 标识。
  private final String etag;

  /// 最后修改时间。
  private final Instant lastModified;
}
