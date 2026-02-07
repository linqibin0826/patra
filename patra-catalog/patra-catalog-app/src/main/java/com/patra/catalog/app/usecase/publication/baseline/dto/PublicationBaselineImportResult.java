package com.patra.catalog.app.usecase.publication.baseline.dto;

/// PubMed Baseline 文献导入结果（Application → Adapter）。
///
/// 文献导入任务执行后返回给调度器的结果摘要。
///
/// **单文件模式**：
///
/// 每次 Job 执行只处理一个文件，结果中包含 fileIndex 标识处理的是哪个文件。
///
/// @param executionId 批处理执行标识符（Spring Batch Job Execution ID）
/// @param baseUrl FTP 基础 URL
/// @param fileIndex 处理的文件索引（1-1334）
/// @param fileName 处理的文件名（如 pubmed26n0001.xml.gz）
/// @param message 人类可读的状态摘要
/// @author linqibin
/// @since 0.1.0
public record PublicationBaselineImportResult(
    Long executionId, String baseUrl, int fileIndex, String fileName, String message) {

  /// 创建成功结果。
  ///
  /// @param executionId 批处理执行标识符
  /// @param baseUrl FTP 基础 URL
  /// @param fileIndex 文件索引
  /// @param fileName 文件名
  /// @return 成功结果对象
  public static PublicationBaselineImportResult success(
      Long executionId, String baseUrl, int fileIndex, String fileName) {
    return new PublicationBaselineImportResult(
        executionId,
        baseUrl,
        fileIndex,
        fileName,
        "PubMed Baseline 导入任务已启动，executionId=%d，fileIndex=%d，fileName=%s"
            .formatted(executionId, fileIndex, fileName));
  }
}
