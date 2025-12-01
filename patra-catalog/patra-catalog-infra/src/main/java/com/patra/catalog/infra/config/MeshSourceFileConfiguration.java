package com.patra.catalog.infra.config;

import com.patra.catalog.domain.port.FileDownloadPort;
import com.patra.catalog.domain.port.MeshSourceFilePort;
import com.patra.catalog.infra.adapter.mesh.DefaultMeshSourceFileAdapter;
import com.patra.catalog.infra.adapter.mesh.MeshSourceFileAdapter;
import com.patra.starter.core.async.AsyncExecutorRegistry;
import com.patra.starter.objectstorage.ObjectStorageOperations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/// MeSH 数据源文件配置。
///
/// 根据对象存储的可用性自动选择合适的实现：
///
/// - **有对象存储**：使用 {@link MeshSourceFileAdapter}（缓存优先策略）
/// - **无对象存储**：使用 {@link DefaultMeshSourceFileAdapter}（直接远程下载）
///
/// 使用 {@link ObjectProvider} 延迟获取依赖，避免对 AutoConfiguration 加载顺序的依赖。
///
/// **配置示例**：
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
/// @author linqibin
/// @since 0.1.0
/// @see MeshSourceFileAdapter
/// @see DefaultMeshSourceFileAdapter
/// @see MeshCacheProperties
@Slf4j
@Configuration
@EnableConfigurationProperties(MeshCacheProperties.class)
public class MeshSourceFileConfiguration {

  /// 创建 MeSH 数据源文件适配器。
  ///
  /// 根据对象存储的可用性自动选择实现：
  /// - **有对象存储**：使用 {@link MeshSourceFileAdapter}（缓存优先策略）
  /// - **无对象存储**：使用 {@link DefaultMeshSourceFileAdapter}（直接远程下载）
  ///
  /// 使用 {@link ObjectProvider} 延迟获取 {@link ObjectStorageOperations}，
  /// 避免对 AutoConfiguration 加载顺序的依赖。
  ///
  /// @param fileDownloadPort 文件下载端口（用于从远程 URL 下载）
  /// @param objectStorageProvider 对象存储操作提供者（延迟注入）
  /// @param cacheProperties 缓存配置属性
  /// @param asyncExecutorRegistry 异步执行器注册表（用于缓存上传）
  /// @return MeSH 数据源文件适配器
  @Bean
  public MeshSourceFilePort meshSourceFileAdapter(
      FileDownloadPort fileDownloadPort,
      ObjectProvider<ObjectStorageOperations> objectStorageProvider,
      MeshCacheProperties cacheProperties,
      AsyncExecutorRegistry asyncExecutorRegistry) {
    ObjectStorageOperations objectStorage = objectStorageProvider.getIfAvailable();
    if (objectStorage != null) {
      log.debug(
          "启用 MeshSourceFileAdapter（缓存模式），bucket={}, keyPrefix={}",
          cacheProperties.bucket(),
          cacheProperties.keyPrefix());
      return new MeshSourceFileAdapter(
          fileDownloadPort, objectStorage, cacheProperties, asyncExecutorRegistry);
    }
    log.debug("启用 DefaultMeshSourceFileAdapter（无缓存模式），对象存储未配置");
    return new DefaultMeshSourceFileAdapter(fileDownloadPort);
  }
}
