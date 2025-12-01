package com.patra.catalog.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/// MeSH 数据缓存配置属性。
///
/// **配置示例:**
///
/// ```yaml
/// patra:
///   catalog:
///     mesh-cache:
///       enabled: true
///       bucket: patra-catalog-cache
///       key-prefix: mesh
/// ```
///
/// @param enabled 是否启用对象存储缓存，默认 `false`，禁用时直接从远程 URL 下载
/// @param bucket 缓存存储桶名称，默认 `patra-catalog-cache`
/// @param keyPrefix 对象键前缀，默认 `mesh`，完整键格式：`{keyPrefix}/descriptors/desc{version}.xml`
/// @author linqibin
/// @since 0.1.0
@ConfigurationProperties(prefix = "patra.catalog.mesh-cache")
public record MeshCacheProperties(boolean enabled, String bucket, String keyPrefix) {

  /// 规范构造函数，提供默认值。
  public MeshCacheProperties {
    if (bucket == null || bucket.isBlank()) {
      bucket = "patra-catalog-cache";
    }
    if (keyPrefix == null || keyPrefix.isBlank()) {
      keyPrefix = "mesh";
    }
  }
}
