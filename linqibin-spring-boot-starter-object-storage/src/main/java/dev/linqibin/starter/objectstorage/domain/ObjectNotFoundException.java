package dev.linqibin.starter.objectstorage.domain;

import lombok.Getter;

/// 表示请求的对象在存储桶中不存在。
///
/// 此异常是永久性错误，不应重试。调用者应该检查存储桶和对象键是否正确，
/// 或者在使用 {@code download} 前先调用 {@code exists} 方法检查对象是否存在。
@Getter
public class ObjectNotFoundException extends DownloadFailedException {

  /// 存储桶名称。
  private final String bucket;

  /// 对象键。
  private final String objectKey;

  /// 构造新的对象不存在异常。
  ///
  /// @param bucket 存储桶名称
  /// @param objectKey 对象键
  public ObjectNotFoundException(String bucket, String objectKey) {
    super(String.format("对象不存在: bucket=%s, key=%s", bucket, objectKey));
    this.bucket = bucket;
    this.objectKey = objectKey;
  }

  /// 构造新的对象不存在异常。
  ///
  /// @param bucket 存储桶名称
  /// @param objectKey 对象键
  /// @param cause 原因异常
  public ObjectNotFoundException(String bucket, String objectKey, Throwable cause) {
    super(String.format("对象不存在: bucket=%s, key=%s", bucket, objectKey), cause);
    this.bucket = bucket;
    this.objectKey = objectKey;
  }
}
