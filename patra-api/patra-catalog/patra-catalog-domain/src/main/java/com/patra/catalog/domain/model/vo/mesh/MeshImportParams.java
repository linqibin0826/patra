package com.patra.catalog.domain.model.vo.mesh;

/// MeSH 批量导入参数值对象。
///
/// 封装批量导入所需的参数，用于 `MeshDescriptorBatchPort.launchImport()` 方法调用。
///
/// **参数说明**：
///
/// - `filePath`：XML 文件路径，不能为空
/// - `meshVersion`：MeSH 版本（如 "2025"），不能为空
/// - `forceNewInstance`：是否强制创建新实例
///   - `false`：幂等执行，相同参数复用 Job 实例，支持断点续传
///   - `true`：强制创建新实例，每次执行都创建新的任务实例
/// - `tempFile`：是否为临时文件（Job 完成后需要清理）
///
/// @author linqibin
/// @since 0.1.0
public record MeshImportParams(
    String filePath, String meshVersion, boolean forceNewInstance, boolean tempFile) {

  /// 创建导入参数。
  ///
  /// @param filePath XML 文件路径
  /// @param meshVersion MeSH 版本
  /// @param forceNewInstance 是否强制创建新实例
  /// @param tempFile 是否为临时文件
  public MeshImportParams {
    if (filePath == null || filePath.isBlank()) {
      throw new IllegalArgumentException("filePath 不能为空");
    }
    if (meshVersion == null || meshVersion.isBlank()) {
      throw new IllegalArgumentException("meshVersion 不能为空");
    }
  }

  /// 创建导入参数（非临时文件）。
  ///
  /// @param filePath XML 文件路径
  /// @param meshVersion MeSH 版本
  /// @param forceNewInstance 是否强制创建新实例
  public MeshImportParams(String filePath, String meshVersion, boolean forceNewInstance) {
    this(filePath, meshVersion, forceNewInstance, false);
  }

  /// 创建增量导入参数（幂等执行，支持断点续传，非临时文件）。
  ///
  /// @param filePath XML 文件路径
  /// @param meshVersion MeSH 版本
  /// @return 导入参数
  public static MeshImportParams incremental(String filePath, String meshVersion) {
    return new MeshImportParams(filePath, meshVersion, false, false);
  }

  /// 创建全量重导入参数（强制创建新实例，非临时文件）。
  ///
  /// @param filePath XML 文件路径
  /// @param meshVersion MeSH 版本
  /// @return 导入参数
  public static MeshImportParams forceNew(String filePath, String meshVersion) {
    return new MeshImportParams(filePath, meshVersion, true, false);
  }
}
