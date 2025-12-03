package com.patra.catalog.infra.config;

import com.patra.catalog.domain.port.FileDownloadPort;
import com.patra.catalog.domain.port.VenueSourceFilePort;
import com.patra.catalog.infra.adapter.venue.DefaultVenueSourceFileAdapter;
import com.patra.catalog.infra.adapter.venue.VenueSourceFileAdapter;
import com.patra.starter.core.async.AsyncExecutorRegistry;
import com.patra.starter.objectstorage.ObjectStorageOperations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/// OpenAlex Venue 数据源文件配置。
///
/// 根据对象存储的可用性自动选择合适的实现：
///
/// - **有对象存储**：使用 {@link VenueSourceFileAdapter}（缓存优先策略）
/// - **无对象存储**：使用 {@link DefaultVenueSourceFileAdapter}（直接远程下载）
///
/// 使用 {@link ObjectProvider} 延迟获取依赖，避免对 AutoConfiguration 加载顺序的依赖。
///
/// **配置示例**：
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
/// @author linqibin
/// @since 0.1.0
/// @see VenueSourceFileAdapter
/// @see DefaultVenueSourceFileAdapter
/// @see OpenAlexCacheProperties
@Slf4j
@Configuration
@EnableConfigurationProperties(OpenAlexCacheProperties.class)
public class VenueSourceFileConfiguration {

  /// 创建 Venue 数据源文件适配器。
  ///
  /// 根据对象存储的可用性自动选择实现：
  /// - **有对象存储**：使用 {@link VenueSourceFileAdapter}（缓存优先策略）
  /// - **无对象存储**：使用 {@link DefaultVenueSourceFileAdapter}（直接远程下载）
  ///
  /// 使用 {@link ObjectProvider} 延迟获取 {@link ObjectStorageOperations}，
  /// 避免对 AutoConfiguration 加载顺序的依赖。
  ///
  /// @param fileDownloadPort 文件下载端口（用于从远程 URL 下载）
  /// @param objectStorageProvider 对象存储操作提供者（延迟注入）
  /// @param cacheProperties 缓存配置属性
  /// @param asyncExecutorRegistry 异步执行器注册表（用于缓存上传）
  /// @return Venue 数据源文件适配器
  @Bean
  public VenueSourceFilePort venueSourceFileAdapter(
      FileDownloadPort fileDownloadPort,
      ObjectProvider<ObjectStorageOperations> objectStorageProvider,
      OpenAlexCacheProperties cacheProperties,
      AsyncExecutorRegistry asyncExecutorRegistry) {
    ObjectStorageOperations objectStorage = objectStorageProvider.getIfAvailable();
    if (objectStorage != null) {
      log.debug(
          "启用 VenueSourceFileAdapter（缓存模式），bucket={}, keyPrefix={}",
          cacheProperties.bucket(),
          cacheProperties.keyPrefix());
      return new VenueSourceFileAdapter(
          fileDownloadPort, objectStorage, cacheProperties, asyncExecutorRegistry);
    }
    log.debug("启用 DefaultVenueSourceFileAdapter（无缓存模式），对象存储未配置");
    return new DefaultVenueSourceFileAdapter(fileDownloadPort, cacheProperties);
  }
}
