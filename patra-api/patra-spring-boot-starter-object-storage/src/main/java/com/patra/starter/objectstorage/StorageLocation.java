package com.patra.starter.objectstorage;

import java.util.Collections;
import java.util.Map;

/// 已解析的存储位置,包含存储桶/对象键对以及必须与文件记录一起持久化的业务元数据。
/// 
/// @param bucket 存储桶名称
/// @param objectKey 对象键
/// @param businessId 业务标识符
/// @param storageKey 存储键(bucket/objectKey)
/// @param correlationData 关联数据(用于下游分析)
public record StorageLocation(
    String bucket,
    String objectKey,
    String businessId,
    String storageKey,
    Map<String, Object> correlationData) {

  public StorageLocation(String bucket, String objectKey, String businessId) {
    this(bucket, objectKey, businessId, bucket + "/" + objectKey, Collections.emptyMap());
  }

  public StorageLocation(
      String bucket, String objectKey, String businessId, Map<String, Object> correlationData) {
    this(bucket, objectKey, businessId, bucket + "/" + objectKey, correlationData);
  }

  public StorageLocation {
    if (!hasText(bucket)) {
      throw new IllegalArgumentException("bucket 不能为空");
    }
    if (!hasText(objectKey)) {
      throw new IllegalArgumentException("objectKey 不能为空");
    }
    if (!hasText(businessId)) {
      throw new IllegalArgumentException("businessId 不能为空");
    }
    storageKey = hasText(storageKey) ? storageKey : bucket + "/" + objectKey;
    correlationData =
        correlationData == null
            ? Collections.emptyMap()
            : Collections.unmodifiableMap(correlationData);
  }

  private static boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }
}
