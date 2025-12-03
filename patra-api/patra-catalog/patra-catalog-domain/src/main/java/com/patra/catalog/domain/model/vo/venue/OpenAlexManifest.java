package com.patra.catalog.domain.model.vo.venue;

import java.util.List;
import java.util.Objects;

/// OpenAlex 数据源清单。
///
/// 封装 OpenAlex S3 manifest 文件的元数据，提供路径转换和统计信息。
///
/// **数据结构**：
///
/// - entries: 分区文件列表，每个 Entry 包含 S3 URL、内容大小和记录数
/// - totalContentLength: 所有文件的总字节数
/// - totalRecordCount: 所有文件的总记录数
///
/// **路径格式**：
///
/// - S3: `s3://openalex/data/sources/updated_date=2025-11-02/part_000.gz`
/// - HTTP: `https://openalex.s3.amazonaws.com/data/sources/updated_date=2025-11-02/part_000.gz`
/// - 相对路径: `updated_date=2025-11-02/part_000.gz`（用于缓存键）
///
/// @author linqibin
/// @since 0.1.0
public record OpenAlexManifest(List<Entry> entries, Long totalContentLength, int totalRecordCount) {

  /// 标准构造函数，确保 entries 非空。
  public OpenAlexManifest {
    Objects.requireNonNull(entries, "entries must not be null");
  }

  /// 获取所有 S3 路径。
  ///
  /// @return S3 URL 列表
  public List<String> getAllS3Paths() {
    return entries.stream().map(Entry::url).toList();
  }

  /// 获取所有 HTTP 路径。
  ///
  /// 将 S3 URL 转换为可通过 HTTP 访问的公开 URL。
  ///
  /// @return HTTP URL 列表
  public List<String> getAllHttpPaths() {
    return entries.stream().map(Entry::toHttpUrl).toList();
  }

  /// 获取所有相对路径（用于缓存键）。
  ///
  /// 提取 `/sources/` 之后的路径部分，格式如 `updated_date=2025-11-02/part_000.gz`。
  ///
  /// @return 相对路径列表
  public List<String> getRelativePaths() {
    return entries.stream().map(Entry::getRelativePath).toList();
  }

  /// 清单条目，表示单个数据文件。
  ///
  /// @param url S3 URL
  /// @param contentLength 文件字节数
  /// @param recordCount 记录数量
  public record Entry(String url, Long contentLength, int recordCount) {

    private static final String S3_PREFIX = "s3://openalex/";
    private static final String HTTP_PREFIX = "https://openalex.s3.amazonaws.com/";
    private static final String SOURCES_PATH_MARKER = "/sources/";

    /// 获取相对路径（用于缓存键）。
    ///
    /// 从 S3 URL 中提取 `/sources/` 之后的部分。
    ///
    /// @return 相对路径，如 `updated_date=2025-11-02/part_000.gz`
    public String getRelativePath() {
      int markerIndex = url.indexOf(SOURCES_PATH_MARKER);
      if (markerIndex == -1) {
        return url;
      }
      return url.substring(markerIndex + SOURCES_PATH_MARKER.length());
    }

    /// 转换为 HTTP URL。
    ///
    /// 将 `s3://openalex/...` 转换为 `https://openalex.s3.amazonaws.com/...`。
    ///
    /// @return HTTP 可访问的公开 URL
    public String toHttpUrl() {
      if (url.startsWith(S3_PREFIX)) {
        return HTTP_PREFIX + url.substring(S3_PREFIX.length());
      }
      return url;
    }
  }
}
