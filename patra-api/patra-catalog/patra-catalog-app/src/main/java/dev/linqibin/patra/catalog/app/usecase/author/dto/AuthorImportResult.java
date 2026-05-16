package dev.linqibin.patra.catalog.app.usecase.author.dto;

/// PubMed Computed Authors 导入结果（Application → Adapter）。
///
/// 导入任务启动后返回给调度器的结果摘要。
///
/// **设计说明**：
///
/// - 大数据量（2100 万+ 条），使用 Spring Batch 批量导入
/// - 异步执行，启动后立即返回 `executionId`
/// - 可通过 `executionId` 在 XXL-Job 控制台或 Spring Batch 控制台追踪执行状态
///
/// @param executionId 批处理执行标识符（Spring Batch Job Execution ID）
/// @param sourceUrl 原始数据源 URL
/// @param message 人类可读的状态摘要
/// @author linqibin
/// @since 0.1.0
public record AuthorImportResult(Long executionId, String sourceUrl, String message) {

  /// 创建成功结果。
  ///
  /// @param executionId 批处理执行标识符
  /// @param sourceUrl 原始数据源 URL
  /// @return 成功结果对象
  public static AuthorImportResult success(Long executionId, String sourceUrl) {
    return new AuthorImportResult(
        executionId,
        sourceUrl,
        String.format(
            "PubMed Computed Authors 导入任务已启动，executionId=%d，sourceUrl=%s", executionId, sourceUrl));
  }
}
