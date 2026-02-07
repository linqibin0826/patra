package com.patra.catalog.domain.model.vo.author;

/// PubMed Computed Authors 批量导入参数值对象。
///
/// 封装批量导入所需的参数，用于 `AuthorBatchPort.launchAuthorImport()` 方法调用。
///
/// **参数说明**：
///
/// - `downloadUrl`：JSON Lines 文件下载 URL，不能为空
///
/// **数据源说明**：
///
/// - NLM FTP 站点的 PubMed Computed Authors JSON Lines 文件
/// - 文件约 3.6GB，包含约 2100 万+ 作者记录
/// - JSON Lines 格式（每行一个 JSON 对象）
/// - 无版本号概念，每周更新
///
/// **设计说明**：
///
/// - 导入操作设计为「一次性初始化」语义，不支持增量或覆盖模式
/// - ItemReader 在 open() 时通过 FileDownloadPort 下载文件到临时目录，从本地文件解析
///
/// @author linqibin
/// @since 0.1.0
public record AuthorImportParams(String downloadUrl) {

  /// 创建导入参数。
  ///
  /// @param downloadUrl JSON Lines 文件下载 URL
  public AuthorImportParams {
    if (downloadUrl == null || downloadUrl.isBlank()) {
      throw new IllegalArgumentException("downloadUrl 不能为空");
    }
  }

  /// 创建导入参数。
  ///
  /// @param downloadUrl JSON Lines 文件下载 URL
  /// @return 导入参数
  public static AuthorImportParams withDownloadUrl(String downloadUrl) {
    return new AuthorImportParams(downloadUrl);
  }
}
