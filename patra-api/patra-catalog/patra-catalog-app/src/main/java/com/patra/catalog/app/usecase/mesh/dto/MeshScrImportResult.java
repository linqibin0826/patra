package com.patra.catalog.app.usecase.mesh.dto;

/// MeSH SCR 导入结果（Application → Adapter）。
///
/// 补充概念记录（Supplementary Concept Record）导入任务执行后返回给调度器的结果摘要。
///
/// **设计说明**：
///
/// 采用流式下载模式，ItemReader 在 open() 时建立 HTTP 连接，无本地临时文件。
///
/// @param executionId 批处理执行标识符（Spring Batch Job Execution ID）
/// @param sourceUrl 原始数据源 URL
/// @param meshVersion MeSH 版本号
/// @param message 人类可读的状态摘要
/// @author linqibin
/// @since 0.1.0
public record MeshScrImportResult(
    Long executionId, String sourceUrl, String meshVersion, String message) {

  /// 创建成功结果。
  ///
  /// @param executionId 批处理执行标识符
  /// @param sourceUrl 原始数据源 URL
  /// @param meshVersion 版本号
  /// @return 成功结果对象
  public static MeshScrImportResult success(
      Long executionId, String sourceUrl, String meshVersion) {
    return new MeshScrImportResult(
        executionId,
        sourceUrl,
        meshVersion,
        String.format(
            "MeSH SCR 导入任务已启动，executionId=%d，sourceUrl=%s，version=%s",
            executionId, sourceUrl, meshVersion));
  }
}
