package com.patra.catalog.app.usecase.mesh.dto;

/// MeSH 主题词导入结果（Application → Adapter）。
///
/// 主题词（Descriptor）导入任务执行后返回给调度器的结果摘要。
///
/// **设计说明**：
///
/// ItemReader 在 open() 时通过 FileDownloadPort 下载文件到临时目录，从本地文件解析。
///
/// @param executionId 批处理执行标识符（Spring Batch Job Execution ID）
/// @param sourceUrl 原始数据源 URL
/// @param meshVersion MeSH 版本号
/// @param message 人类可读的状态摘要
/// @author linqibin
/// @since 0.1.0
public record MeshDescriptorImportResult(
    Long executionId, String sourceUrl, String meshVersion, String message) {

  /// 创建成功结果。
  ///
  /// @param executionId 批处理执行标识符
  /// @param sourceUrl 原始数据源 URL
  /// @param meshVersion 版本号
  /// @return 成功结果对象
  public static MeshDescriptorImportResult success(
      Long executionId, String sourceUrl, String meshVersion) {
    return new MeshDescriptorImportResult(
        executionId,
        sourceUrl,
        meshVersion,
        String.format(
            "MeSH 导入任务已启动，executionId=%d，sourceUrl=%s，version=%s",
            executionId, sourceUrl, meshVersion));
  }
}
