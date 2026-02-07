package com.patra.catalog.infra.adapter.source;

import com.patra.catalog.domain.model.vo.venue.OpenAlexManifest;
import com.patra.catalog.domain.port.source.StreamingDownloadPort;
import com.patra.catalog.domain.port.source.StreamingDownloadResult;
import com.patra.catalog.domain.port.source.VenueSourceFilePort;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// OpenAlex Venue 数据源文件适配器。
///
/// 直接从 OpenAlex AWS S3 公开存储桶获取 Sources 数据。
///
/// **数据源**：
///
/// - Manifest：`https://openalex.s3.amazonaws.com/data/sources/manifest`
/// - 分区文件 URL 已包含在 Manifest 中
///
/// **流式处理特性**：
///
/// - Manifest 解析：无磁盘落盘，HTTP 响应体直接传递给 JSON 解析器
/// - 分区文件：通过 Manifest 获取 URL 列表，由 ItemReader 按需下载到临时文件
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

  private final StreamingDownloadPort streamingDownloadPort;

  /// 流式获取并解析 OpenAlex Sources manifest。
  ///
  /// **流式处理特性**：
  ///
  /// - 无磁盘落盘，HTTP 响应体直接传递给 JSON 解析器
  /// - 使用 try-with-resources 自动管理 HTTP 连接
  ///
  /// @return 解析后的 manifest 对象
  @Override
  public OpenAlexManifest fetchManifest() {
    String manifestUrl = getManifestUrl();
    log.info("流式获取 OpenAlex Sources manifest: {}", manifestUrl);

    try (StreamingDownloadResult downloadResult =
        streamingDownloadPort.download(URI.create(manifestUrl))) {

      log.debug("HTTP 连接建立成功，开始解析 manifest JSON");
      return OpenAlexManifestParser.parseManifest(downloadResult.inputStream());

    } catch (Exception e) {
      throw new RuntimeException("获取 OpenAlex manifest 失败: " + e.getMessage(), e);
    }
  }

  /// 获取 manifest 文件的完整 URL。
  ///
  /// @return manifest URL（如 `https://openalex.s3.amazonaws.com/data/sources/manifest`）
  private String getManifestUrl() {
    return S3_BASE_URL + "/" + S3_SOURCES_PATH + "/manifest";
  }
}
