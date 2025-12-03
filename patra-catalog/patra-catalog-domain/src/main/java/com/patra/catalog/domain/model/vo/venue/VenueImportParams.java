package com.patra.catalog.domain.model.vo.venue;

import java.util.List;

/// Venue 批量导入参数值对象。
///
/// 封装批量导入所需的参数，用于 `VenueImportBatchPort.launchImport()` 方法调用。
///
/// **参数说明**：
///
/// - `filePaths`：JSON Lines 文件路径列表（.gz 压缩），至少包含一个文件
/// - `forceNewInstance`：是否强制创建新实例
///   - `false`：幂等执行，相同参数复用 Job 实例，支持断点续传
///   - `true`：强制创建新实例，每次执行都创建新的任务实例
/// - `tempFiles`：是否为临时文件（Job 完成后需要清理）
///
/// **与 MeshImportParams 的差异**：
///
/// - MeSH 使用单个 XML 文件路径，Venue 使用多个 .gz 文件路径列表
/// - Venue 无需版本号（OpenAlex 通过 updated_date 分区管理版本）
///
/// @author linqibin
/// @since 0.1.0
public record VenueImportParams(
    List<String> filePaths, boolean forceNewInstance, boolean tempFiles) {

  /// 创建导入参数。
  ///
  /// @param filePaths JSON Lines 文件路径列表
  /// @param forceNewInstance 是否强制创建新实例
  /// @param tempFiles 是否为临时文件
  public VenueImportParams {
    if (filePaths == null || filePaths.isEmpty()) {
      throw new IllegalArgumentException("filePaths 不能为空");
    }
  }

  /// 创建增量导入参数（幂等执行，支持断点续传，非临时文件）。
  ///
  /// @param filePaths 文件路径列表
  /// @return 导入参数
  public static VenueImportParams incremental(List<String> filePaths) {
    return new VenueImportParams(filePaths, false, false);
  }

  /// 创建全量重导入参数（强制创建新实例，非临时文件）。
  ///
  /// @param filePaths 文件路径列表
  /// @return 导入参数
  public static VenueImportParams forceNew(List<String> filePaths) {
    return new VenueImportParams(filePaths, true, false);
  }

  /// 创建带临时文件标记的导入参数。
  ///
  /// @param filePaths 文件路径列表
  /// @param forceNewInstance 是否强制创建新实例
  /// @return 导入参数
  public static VenueImportParams withTempFiles(List<String> filePaths, boolean forceNewInstance) {
    return new VenueImportParams(filePaths, forceNewInstance, true);
  }

  /// 获取文件路径的逗号分隔字符串。
  ///
  /// 用于 Spring Batch Job 参数序列化。
  ///
  /// @return 逗号分隔的路径字符串
  public String getFilePathsAsString() {
    return String.join(",", filePaths);
  }

  /// 获取文件数量。
  ///
  /// @return 文件数量
  public int getFileCount() {
    return filePaths.size();
  }
}
