package com.patra.catalog.domain.model.vo.mesh;

/// MeSH 批量导入参数值对象。
///
/// 封装批量导入所需的参数，用于 `MeshDescriptorBatchPort.launchImport()` 方法调用。
///
/// **参数说明**：
///
/// - `filePath`：XML 文件路径，不能为空
/// - `meshVersion`：MeSH 版本（如 "2025"），不能为空
/// - `tempFile`：是否为临时文件（Job 完成后需要清理）
///
/// **设计说明**：
///
/// 导入操作设计为「一次性初始化」语义，不支持增量或覆盖模式。
/// 每次导入都创建新的 Job 实例，相同参数不会复用旧实例。
///
/// @author linqibin
/// @since 0.1.0
public record MeshImportParams(String filePath, String meshVersion, boolean tempFile) {

  /// 创建导入参数。
  ///
  /// @param filePath XML 文件路径
  /// @param meshVersion MeSH 版本
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
  /// @return 导入参数
  public static MeshImportParams of(String filePath, String meshVersion) {
    return new MeshImportParams(filePath, meshVersion, false);
  }

  /// 创建带临时文件标记的导入参数。
  ///
  /// @param filePath XML 文件路径
  /// @param meshVersion MeSH 版本
  /// @return 导入参数
  public static MeshImportParams withTempFile(String filePath, String meshVersion) {
    return new MeshImportParams(filePath, meshVersion, true);
  }
}
