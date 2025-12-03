package com.patra.catalog.domain.port;

import com.patra.catalog.domain.exception.FileDownloadException;
import com.patra.catalog.domain.model.vo.venue.OpenAlexManifest;
import java.nio.file.Path;
import java.util.List;

/// OpenAlex Venue 数据源文件端口。
///
/// 负责获取 OpenAlex Sources 数据文件（manifest 和 JSON Lines 分区文件）到本地临时目录。
/// 实现可决定从对象存储缓存或 AWS S3 公开存储桶获取。
///
/// **设计原则**：
///
/// - Domain 层定义接口，隐藏缓存策略实现细节
/// - Infrastructure 层实现缓存优先策略（MinIO 缓存 → AWS S3 下载 → 异步上传缓存）
/// - 返回本地文件路径，调用方无需关心数据来源
///
/// **与 MeshSourceFilePort 的差异**：
///
/// - MeSH 使用单个 XML 文件，Venue 使用 manifest + 多个 .gz 分区文件
/// - MeSH 使用版本号区分，OpenAlex 使用 updated_date 分区
/// - Venue 需要先获取 manifest 解析分区列表，再批量下载分区文件
///
/// **数据源格式**：
///
/// - Manifest: `https://openalex.s3.amazonaws.com/data/sources/manifest`（JSON 格式）
/// - 分区文件: `https://openalex.s3.amazonaws.com/data/sources/updated_date=YYYY-MM-DD/part_XXX.gz`
///
/// @author linqibin
/// @since 0.1.0
public interface VenueSourceFilePort {

  /// 获取 OpenAlex Sources manifest 文件。
  ///
  /// Manifest 文件包含所有分区文件的元数据（URL、大小、记录数）。
  ///
  /// @return 解析后的 manifest 对象
  /// @throws FileDownloadException 获取 manifest 失败时
  OpenAlexManifest fetchManifest();

  /// 获取指定分区文件到本地临时目录。
  ///
  /// 优先从对象存储缓存获取，如果缓存不存在则从 AWS S3 下载。
  /// 下载后会异步上传到对象存储作为缓存。
  ///
  /// @param relativePath 相对路径（如 `updated_date=2025-11-02/part_000.gz`）
  /// @return 本地临时文件路径
  /// @throws FileDownloadException 获取文件失败时
  Path fetchPartitionFile(String relativePath);

  /// 批量获取所有分区文件到本地临时目录。
  ///
  /// 根据 manifest 中的分区列表，依次下载所有 .gz 文件。
  ///
  /// @param manifest 包含分区信息的 manifest
  /// @return 本地临时文件路径列表（与 manifest 中的顺序一致）
  /// @throws FileDownloadException 获取任何文件失败时
  List<Path> fetchAllPartitionFiles(OpenAlexManifest manifest);
}
