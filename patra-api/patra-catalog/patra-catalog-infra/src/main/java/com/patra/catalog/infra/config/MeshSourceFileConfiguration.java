package com.patra.catalog.infra.config;

import com.patra.catalog.domain.port.FileDownloadPort;
import com.patra.catalog.domain.port.MeshSourceFilePort;
import com.patra.catalog.infra.adapter.mesh.DefaultMeshSourceFileAdapter;
import com.patra.catalog.infra.adapter.mesh.MeshSourceFileAdapter;
import com.patra.starter.core.async.AsyncExecutorRegistry;
import com.patra.starter.objectstorage.ObjectStorageOperations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/// MeSH 数据源文件自动配置。
///
/// 根据对象存储的可用性自动选择合适的实现：
///
/// - **有对象存储时**：使用 {@link MeshSourceFileAdapter}（缓存优先策略）
/// - **无对象存储时**：使用 {@link DefaultMeshSourceFileAdapter}（直接远程下载）
///
/// **条件装配规则**：
///
/// ```
/// ObjectStorageOperations Bean 存在?
/// ├── 是 → MeshSourceFileAdapter（MinIO 缓存优先）
/// └── 否 → DefaultMeshSourceFileAdapter（直接远程下载）
/// ```
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
@Configuration
@EnableConfigurationProperties(MeshCacheProperties.class)
public class MeshSourceFileConfiguration {

  /// 创建带缓存功能的 MeSH 数据源文件适配器。
  ///
  /// **激活条件**：容器中存在 {@link ObjectStorageOperations} Bean。
  ///
  /// 实现缓存优先策略：
  /// 1. 检查对象存储中是否存在缓存文件
  /// 2. 存在则从对象存储下载到本地临时目录
  /// 3. 不存在则从远程 URL 下载，并异步上传到对象存储
  ///
  /// @param fileDownloadPort 文件下载端口（用于从远程 URL 下载）
  /// @param objectStorage 对象存储操作（用于缓存读写）
  /// @param cacheProperties 缓存配置属性
  /// @param asyncExecutorRegistry 异步执行器注册表（用于缓存上传）
  /// @return 带缓存功能的 MeSH 数据源文件适配器
  @Bean
  @ConditionalOnBean(ObjectStorageOperations.class)
  public MeshSourceFilePort meshSourceFileAdapter(
      FileDownloadPort fileDownloadPort,
      ObjectStorageOperations objectStorage,
      MeshCacheProperties cacheProperties,
      AsyncExecutorRegistry asyncExecutorRegistry) {
    return new MeshSourceFileAdapter(
        fileDownloadPort, objectStorage, cacheProperties, asyncExecutorRegistry);
  }

  /// 创建默认的 MeSH 数据源文件适配器（无缓存）。
  ///
  /// **激活条件**：容器中不存在 {@link MeshSourceFilePort} Bean。
  ///
  /// 作为回退实现，直接从远程 URL 下载文件，不使用缓存。
  /// 适用于：
  /// - 对象存储未配置
  /// - 不需要缓存功能的场景
  /// - 快速验证/测试场景
  ///
  /// @param fileDownloadPort 文件下载端口
  /// @return 默认的 MeSH 数据源文件适配器
  @Bean
  @ConditionalOnMissingBean(MeshSourceFilePort.class)
  public MeshSourceFilePort defaultMeshSourceFileAdapter(FileDownloadPort fileDownloadPort) {
    return new DefaultMeshSourceFileAdapter(fileDownloadPort);
  }
}
