package com.patra.catalog.infra.adapter.venue;

import com.patra.catalog.domain.model.vo.venue.OpenAlexManifest;
import com.patra.catalog.domain.port.FileDownloadPort;
import com.patra.catalog.domain.port.VenueSourceFilePort;
import com.patra.catalog.infra.config.OpenAlexCacheProperties;
import com.patra.catalog.infra.config.VenueSourceFileConfiguration;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/// OpenAlex Venue 数据源文件默认适配器。
///
/// 当对象存储未配置时作为回退实现，直接从 AWS S3 公开存储桶下载文件（无缓存）。
///
/// **使用场景**：
///
/// - 对象存储未配置（没有 `ObjectStorageOperations` Bean）
/// - 生产环境不需要缓存功能
/// - 快速验证/测试场景
///
/// **与 VenueSourceFileAdapter 的差异**：
///
/// - 不依赖 ObjectStorageOperations
/// - 不进行缓存读写
/// - 每次都从远程下载
///
/// @author linqibin
/// @since 0.1.0
/// @see VenueSourceFileConfiguration
@Slf4j
public class DefaultVenueSourceFileAdapter implements VenueSourceFilePort {

  private final FileDownloadPort fileDownloadPort;
  private final OpenAlexCacheProperties cacheProperties;

  /// 构造默认 Venue 数据源文件适配器。
  ///
  /// @param fileDownloadPort 文件下载端口（用于从远程 URL 下载）
  /// @param cacheProperties 缓存配置属性（用于获取 S3 URL）
  public DefaultVenueSourceFileAdapter(
      FileDownloadPort fileDownloadPort, OpenAlexCacheProperties cacheProperties) {
    this.fileDownloadPort = fileDownloadPort;
    this.cacheProperties = cacheProperties;
  }

  @Override
  public OpenAlexManifest fetchManifest() {
    log.info("直接从远程下载 OpenAlex Sources manifest（无缓存）: {}", cacheProperties.getManifestUrl());

    Path manifestFile =
        fileDownloadPort.downloadToTemp(URI.create(cacheProperties.getManifestUrl()));

    try {
      return OpenAlexManifestParser.parseManifest(manifestFile);
    } finally {
      OpenAlexManifestParser.cleanupTempFile(manifestFile);
    }
  }

  @Override
  public Path fetchPartitionFile(String relativePath) {
    String url = cacheProperties.getPartitionUrl(relativePath);
    log.debug("直接从远程下载 OpenAlex 分区文件（无缓存）: {}", url);
    return fileDownloadPort.downloadToTemp(URI.create(url));
  }

  @Override
  public List<Path> fetchAllPartitionFiles(OpenAlexManifest manifest) {
    log.info("开始获取所有分区文件（无缓存），共 {} 个", manifest.entries().size());

    List<Path> localFiles = new ArrayList<>();
    int total = manifest.entries().size();
    int current = 0;

    for (String relativePath : manifest.getRelativePaths()) {
      current++;
      if (current % 10 == 0 || current == total) {
        log.info("下载进度: {}/{}", current, total);
      }

      Path localFile = fetchPartitionFile(relativePath);
      localFiles.add(localFile);
    }

    log.info("所有分区文件获取完成，共 {} 个", localFiles.size());
    return localFiles;
  }
}
