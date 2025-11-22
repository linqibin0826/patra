package com.patra.starter.objectstorage;

/// 支持的对象存储提供商。
public enum ProviderType {
  MINIO,
  S3;

  /// 从字符串解析提供商类型。
  ///
  /// @param provider 提供商名称
  /// @return 对应的提供商类型,默认为 MINIO
  /// @throws IllegalArgumentException 如果提供商类型不支持
  public static ProviderType from(String provider) {
    if (provider == null || provider.isBlank()) {
      return MINIO;
    }
    for (ProviderType type : values()) {
      if (type.name().equalsIgnoreCase(provider)) {
        return type;
      }
    }
    throw new IllegalArgumentException("不支持的提供商类型: " + provider);
  }
}
