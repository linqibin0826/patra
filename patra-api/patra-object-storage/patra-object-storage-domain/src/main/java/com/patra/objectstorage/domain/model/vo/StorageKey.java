package com.patra.objectstorage.domain.model.vo;

import java.util.Objects;

/**
 * 不可变的存储定位符,包含存储桶和对象键。
 *
 * <p>存储键是文件在对象存储中的唯一标识,由存储桶名称和对象键路径组成。这个值对象封装了存储定位逻辑, 提供了验证、组合和比较功能,确保存储键的完整性和一致性。
 *
 * @param bucket 逻辑存储桶名称(不能为空)
 * @param objectKey 存储桶内的对象键路径(不能为空)
 */
public record StorageKey(String bucket, String objectKey) {

  /**
   * 创建新的存储键并验证必需部分。
   *
   * @throws IllegalArgumentException 如果存储桶或对象键为空
   */
  public StorageKey {
    if (bucket == null || bucket.isBlank()) {
      throw new IllegalArgumentException("存储桶不能为空");
    }
    if (objectKey == null || objectKey.isBlank()) {
      throw new IllegalArgumentException("对象键不能为空");
    }
  }

  /**
   * 构建用于持久化和幂等性检查的规范存储键字符串。
   *
   * <p>将存储桶和对象键组合成统一的字符串表示形式,格式为 "bucket/objectKey", 用作数据库唯一约束字段,确保同一文件不会重复记录。
   *
   * @return 组合的存储键形式(例如 "literature-files/2024/01/article.pdf")
   */
  public String fullKey() {
    return bucket + '/' + objectKey;
  }

  /**
   * 便捷方法,用于与另一个原始存储桶/键对进行相等性比较。
   *
   * @param otherBucket 要比较的存储桶名称
   * @param otherKey 要比较的对象键
   * @return 如果两个部分都匹配此键,则返回 {@code true}
   */
  public boolean matches(String otherBucket, String otherKey) {
    return Objects.equals(bucket, otherBucket) && Objects.equals(objectKey, otherKey);
  }
}
