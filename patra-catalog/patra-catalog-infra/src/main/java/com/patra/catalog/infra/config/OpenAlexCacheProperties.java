package com.patra.catalog.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/// OpenAlex 数据缓存配置属性。
///
/// **配置示例:**
///
/// ```yaml
/// patra:
///   catalog:
///     openalex-cache:
///       enabled: true
///       bucket: patra-catalog-cache
///       key-prefix: openalex/sources
///       s3-base-url: https://openalex.s3.amazonaws.com
///       s3-sources-path: data/sources
/// ```
///
/// **缓存策略**：
///
/// 1. 检查 MinIO 缓存是否存在
/// 2. 存在则从 MinIO 下载到本地临时目录
/// 3. 不存在则从 AWS S3 公开存储桶下载，并异步上传到 MinIO
///
/// **S3 公开存储桶说明**：
///
/// OpenAlex 数据托管在 AWS S3 公开存储桶，无需认证即可通过 HTTPS 访问。
/// - Manifest: `{s3BaseUrl}/{s3SourcesPath}/manifest`
/// - 分区文件: `{s3BaseUrl}/{s3SourcesPath}/updated_date=YYYY-MM-DD/part_XXX.gz`
///
/// @param enabled 是否启用对象存储缓存，默认 `false`
/// @param bucket 缓存存储桶名称，默认 `patra-catalog-cache`
/// @param keyPrefix 对象键前缀，默认 `openalex/sources`
/// @param s3BaseUrl AWS S3 公开存储桶 HTTPS 基地址
/// @param s3SourcesPath S3 中 Sources 数据路径
/// @author linqibin
/// @since 0.1.0
@ConfigurationProperties(prefix = "patra.catalog.openalex-cache")
public record OpenAlexCacheProperties(
    boolean enabled, String bucket, String keyPrefix, String s3BaseUrl, String s3SourcesPath) {

  /// 默认 S3 基地址。
  public static final String DEFAULT_S3_BASE_URL = "https://openalex.s3.amazonaws.com";

  /// 默认 Sources 路径。
  public static final String DEFAULT_S3_SOURCES_PATH = "data/sources";

  /// 规范构造函数，提供默认值。
  public OpenAlexCacheProperties {
    if (bucket == null || bucket.isBlank()) {
      bucket = "patra-catalog-cache";
    }
    if (keyPrefix == null || keyPrefix.isBlank()) {
      keyPrefix = "openalex/sources";
    }
    if (s3BaseUrl == null || s3BaseUrl.isBlank()) {
      s3BaseUrl = DEFAULT_S3_BASE_URL;
    }
    if (s3SourcesPath == null || s3SourcesPath.isBlank()) {
      s3SourcesPath = DEFAULT_S3_SOURCES_PATH;
    }
  }

  /// 获取 manifest 文件的完整 URL。
  ///
  /// @return manifest URL（如 `https://openalex.s3.amazonaws.com/data/sources/manifest`）
  public String getManifestUrl() {
    return s3BaseUrl + "/" + s3SourcesPath + "/manifest";
  }

  /// 获取分区文件的完整 URL。
  ///
  /// @param relativePath 相对路径（如 `updated_date=2025-11-02/part_000.gz`）
  /// @return 完整 URL
  public String getPartitionUrl(String relativePath) {
    return s3BaseUrl + "/" + s3SourcesPath + "/" + relativePath;
  }

  /// 获取分区文件缓存键。
  ///
  /// @param relativePath 相对路径（如 `updated_date=2025-11-02/part_000.gz`）
  /// @return 缓存对象键（如 `openalex/sources/updated_date=2025-11-02/part_000.gz`）
  public String getCacheKey(String relativePath) {
    return keyPrefix + "/" + relativePath;
  }
}
