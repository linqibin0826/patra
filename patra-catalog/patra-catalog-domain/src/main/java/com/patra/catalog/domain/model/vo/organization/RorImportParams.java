package com.patra.catalog.domain.model.vo.organization;

import com.patra.catalog.domain.exception.InvalidRorImportParamsException;

/// ROR 机构批量导入参数值对象。
///
/// 封装批量导入所需的参数，用于 `RorOrganizationBatchPort.launchImport()` 方法调用。
///
/// **参数说明**：
///
/// - `downloadUrl`：ROR Data Dump JSON 文件下载 URL，不能为空
/// - `rorVersion`：ROR 版本号（如 "v1.63"），不能为空
///
/// **设计说明**：
///
/// - 导入操作设计为「一次性初始化」语义，不支持增量或覆盖模式
/// - ItemReader 在 open() 时通过 FileDownloadPort 下载文件到临时目录，从本地文件解析
///
/// @author linqibin
/// @since 0.1.0
public record RorImportParams(String downloadUrl, String rorVersion) {

  /// 创建导入参数。
  ///
  /// @param downloadUrl JSON 文件下载 URL
  /// @param rorVersion ROR 版本号
  public RorImportParams {
    if (downloadUrl == null || downloadUrl.isBlank()) {
      throw new InvalidRorImportParamsException("downloadUrl 不能为空");
    }
    if (rorVersion == null || rorVersion.isBlank()) {
      throw new InvalidRorImportParamsException("rorVersion 不能为空");
    }
  }

  /// 创建导入参数。
  ///
  /// @param downloadUrl JSON 文件下载 URL
  /// @param rorVersion ROR 版本号
  /// @return 导入参数
  public static RorImportParams withDownloadUrl(String downloadUrl, String rorVersion) {
    return new RorImportParams(downloadUrl, rorVersion);
  }
}
