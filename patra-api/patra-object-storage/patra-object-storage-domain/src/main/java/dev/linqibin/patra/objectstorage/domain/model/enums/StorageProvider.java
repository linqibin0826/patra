package dev.linqibin.patra.objectstorage.domain.model.enums;

import java.util.Arrays;

/// 平台支持的对象存储提供商枚举。
///
/// 定义了Patra平台可以集成的各种对象存储服务提供商类型,包括开源和商业云存储解决方案。
public enum StorageProvider {
  /// MinIO 对象存储
  MINIO,

  /// Amazon S3 对象存储
  S3,

  /// 阿里云对象存储服务
  OSS,

  /// 腾讯云对象存储
  COS;

  /// 根据名称(不区分大小写)解析存储提供商。
  ///
  /// @param value 调用方提供的存储提供商文本表示
  /// @return 匹配的 {@link StorageProvider}
  /// @throws IllegalArgumentException 如果提供的值不被支持
  public static StorageProvider fromName(String value) {
    return Arrays.stream(values())
        .filter(provider -> provider.name().equalsIgnoreCase(value))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("不支持的存储提供商: " + value));
  }
}
