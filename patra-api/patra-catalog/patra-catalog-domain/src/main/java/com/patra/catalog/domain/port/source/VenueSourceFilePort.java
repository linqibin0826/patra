package com.patra.catalog.domain.port.source;

import com.patra.catalog.domain.exception.FileDownloadException;
import com.patra.catalog.domain.model.vo.venue.OpenAlexManifest;

/// OpenAlex Venue 数据源端口。
///
/// 负责从 OpenAlex AWS S3 公开存储桶获取 Sources manifest 索引文件。
///
/// **数据源**：
///
/// - Manifest：`https://openalex.s3.amazonaws.com/data/sources/manifest`（JSON 格式，动态索引）
/// - 分区文件 URL 已包含在 Manifest 的 entries 中
///
/// **流式处理特性**：
///
/// - Manifest 解析：无磁盘落盘，HTTP 响应体直接传递给 JSON 解析器
/// - 分区文件：通过 Manifest 获取 URL 列表，由 ItemReader 按需流式下载
///
/// **设计原则**：
///
/// - Domain 层定义接口，Infrastructure 层提供实现
/// - 分区文件的下载由批处理 ItemReader 按需完成，不在此端口处理
///
/// @author linqibin
/// @since 0.1.0
/// @see <a href="https://docs.openalex.org/download-all-data/openalex-snapshot">OpenAlex
// Snapshot</a>
public interface VenueSourceFilePort {

  /// 流式获取并解析 OpenAlex Sources manifest。
  ///
  /// Manifest 文件包含所有分区文件的元数据（URL、大小、记录数）。
  /// 每次导入时从远程获取以确保获取最新的分区列表。
  ///
  /// **流式处理**：无磁盘落盘，HTTP 响应体直接传递给 JSON 解析器。
  ///
  /// @return 解析后的 manifest 对象
  /// @throws FileDownloadException 获取 manifest 失败时
  OpenAlexManifest fetchManifest();
}
