package com.patra.catalog.domain.model.vo.venue;

import java.util.List;

/// Venue 批量导入参数值对象。
///
/// 封装批量导入所需的参数，用于 `VenueImportBatchPort.launchImport()` 方法调用。
///
/// **参数说明**：
///
/// - `filePaths`：JSON Lines 文件路径列表（.gz 压缩），至少包含一个文件
/// - `tempFiles`：是否为临时文件（Job 完成后需要清理）
///
/// **设计说明**：
///
/// 导入操作设计为「一次性初始化」语义，不支持增量或覆盖模式。
/// 每次导入都创建新的 Job 实例，相同参数不会复用旧实例。
///
/// @author linqibin
/// @since 0.1.0
public record VenueImportParams(List<String> filePaths, boolean tempFiles) {

  /// 创建导入参数。
  ///
  /// @param filePaths JSON Lines 文件路径列表
  /// @param tempFiles 是否为临时文件
  public VenueImportParams {
    if (filePaths == null || filePaths.isEmpty()) {
      throw new IllegalArgumentException("filePaths 不能为空");
    }
  }

  /// 创建导入参数（非临时文件）。
  ///
  /// @param filePaths 文件路径列表
  /// @return 导入参数
  public static VenueImportParams of(List<String> filePaths) {
    return new VenueImportParams(filePaths, false);
  }

  /// 创建带临时文件标记的导入参数。
  ///
  /// @param filePaths 文件路径列表
  /// @return 导入参数
  public static VenueImportParams withTempFiles(List<String> filePaths) {
    return new VenueImportParams(filePaths, true);
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
