package com.patra.catalog.infra.adapter.source;

import com.patra.catalog.domain.model.vo.venue.OpenAlexManifest;
import com.patra.catalog.domain.port.FileDownloadPort;
import com.patra.catalog.domain.port.VenueSourceFilePort;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// OpenAlex Venue 数据源文件适配器。
///
/// 直接从 OpenAlex AWS S3 公开存储桶下载 Sources 数据文件。
///
/// **数据源**：
///
/// - Manifest：`https://openalex.s3.amazonaws.com/data/sources/manifest`
/// - 分区文件：`https://openalex.s3.amazonaws.com/data/sources/updated_date=YYYY-MM-DD/part_XXX.gz`
///
/// **说明**：
///
/// OpenAlex 数据托管在 AWS S3 公开存储桶，无需认证即可通过 HTTPS 访问。
/// Manifest 是动态索引文件，每次导入时从远程获取以确保获取最新的分区列表。
///
/// @author linqibin
/// @since 0.1.0
/// @see <a href="https://docs.openalex.org/download-all-data/openalex-snapshot">OpenAlex
// Snapshot</a>
@Slf4j
@Component
@RequiredArgsConstructor
public class VenueSourceFileAdapter implements VenueSourceFilePort {

  /// OpenAlex S3 公开存储桶基地址。
  private static final String S3_BASE_URL = "https://openalex.s3.amazonaws.com";

  /// Sources 数据路径。
  private static final String S3_SOURCES_PATH = "data/sources";

  private final FileDownloadPort fileDownloadPort;

  @Override
  public OpenAlexManifest fetchManifest() {
    String manifestUrl = getManifestUrl();
    log.info("从远程下载 OpenAlex Sources manifest: {}", manifestUrl);

    Path manifestFile = fileDownloadPort.downloadToTemp(URI.create(manifestUrl));

    try {
      return OpenAlexManifestParser.parseManifest(manifestFile);
    } finally {
      OpenAlexManifestParser.cleanupTempFile(manifestFile);
    }
  }

  @Override
  public Path fetchPartitionFile(String relativePath) {
    String url = getPartitionUrl(relativePath);
    log.debug("从远程下载 OpenAlex 分区文件: {}", url);
    return fileDownloadPort.downloadToTemp(URI.create(url));
  }

  @Override
  public List<Path> fetchAllPartitionFiles(OpenAlexManifest manifest) {
    log.info("开始获取所有分区文件，共 {} 个", manifest.entries().size());

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

  /// 获取 manifest 文件的完整 URL。
  ///
  /// @return manifest URL（如 `https://openalex.s3.amazonaws.com/data/sources/manifest`）
  private String getManifestUrl() {
    return S3_BASE_URL + "/" + S3_SOURCES_PATH + "/manifest";
  }

  /// 获取分区文件的完整 URL。
  ///
  /// @param relativePath 相对路径（如 `updated_date=2025-11-02/part_000.gz`）
  /// @return 完整 URL
  private String getPartitionUrl(String relativePath) {
    return S3_BASE_URL + "/" + S3_SOURCES_PATH + "/" + relativePath;
  }
}
