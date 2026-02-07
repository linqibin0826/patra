package com.patra.catalog.domain.model.vo.mesh;

/// MeSH 批量导入参数值对象。
///
/// 封装批量导入所需的参数，用于 `MeshBatchPort.launchDescriptorImport()` 与
/// `MeshBatchPort.launchScrImport()` 方法调用。
///
/// **参数说明**：
///
/// - `downloadUrl`：XML 文件下载 URL，不能为空
/// - `meshVersion`：MeSH 版本（如 "2025"），不能为空
///
/// **设计说明**：
///
/// - 导入操作设计为「一次性初始化」语义，不支持增量或覆盖模式
/// - ItemReader 在 open() 时通过 FileDownloadPort 下载文件到临时目录，从本地文件解析
///
/// @author linqibin
/// @since 0.1.0
public record MeshImportParams(String downloadUrl, String meshVersion) {

  /// 创建导入参数。
  ///
  /// @param downloadUrl XML 文件下载 URL
  /// @param meshVersion MeSH 版本
  public MeshImportParams {
    if (downloadUrl == null || downloadUrl.isBlank()) {
      throw new IllegalArgumentException("downloadUrl 不能为空");
    }
    if (meshVersion == null || meshVersion.isBlank()) {
      throw new IllegalArgumentException("meshVersion 不能为空");
    }
  }

  /// 创建导入参数。
  ///
  /// @param downloadUrl XML 文件下载 URL
  /// @param meshVersion MeSH 版本
  /// @return 导入参数
  public static MeshImportParams withDownloadUrl(String downloadUrl, String meshVersion) {
    return new MeshImportParams(downloadUrl, meshVersion);
  }
}
