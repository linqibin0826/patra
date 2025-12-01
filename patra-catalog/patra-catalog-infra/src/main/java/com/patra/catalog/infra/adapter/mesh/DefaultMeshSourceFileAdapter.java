package com.patra.catalog.infra.adapter.mesh;

import com.patra.catalog.domain.port.FileDownloadPort;
import com.patra.catalog.domain.port.MeshSourceFilePort;
import java.net.URI;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/// MeSH 数据源文件默认适配器。
///
/// 当对象存储未配置时作为回退实现，直接从远程 URL 下载文件。
///
/// **使用场景**：
///
/// - 对象存储未配置（没有 `ObjectStorageOperations` Bean）
/// - 生产环境不需要缓存功能
/// - 快速验证/测试场景
///
/// @author linqibin
/// @since 0.1.0
/// @see com.patra.catalog.infra.config.MeshSourceFileConfiguration
@Slf4j
@RequiredArgsConstructor
public class DefaultMeshSourceFileAdapter implements MeshSourceFilePort {

  private final FileDownloadPort fileDownloadPort;

  @Override
  public Path fetchDescriptorFile(String meshVersion, URI remoteUrl) {
    log.info("直接从远程下载 MeSH Descriptor 文件（无缓存）: {}", remoteUrl);
    return fileDownloadPort.downloadToTemp(remoteUrl);
  }

  @Override
  public Path fetchQualifierFile(String meshVersion, URI remoteUrl) {
    log.info("直接从远程下载 MeSH Qualifier 文件（无缓存）: {}", remoteUrl);
    return fileDownloadPort.downloadToTemp(remoteUrl);
  }
}
