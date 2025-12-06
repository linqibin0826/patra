package com.patra.catalog.app.usecase.serfile.dto;

/// NLM Serfile 导入结果（Application → Adapter）。
///
/// Serfile 导入任务执行后返回给调度器的结果摘要。
///
/// @param totalParsed 解析的 Serial 记录总数
/// @param updatedCount 更新的现有期刊数量（匹配到 OpenAlex 记录）
/// @param createdCount 新创建的期刊数量（PubMed 独有）
/// @param skippedCount 跳过的记录数量（匹配失败或数据异常）
/// @param serfileVersion Serfile 版本号
/// @param sourceUrl 原始数据源 URL
/// @param durationMillis 导入耗时（毫秒）
/// @param message 人类可读的状态摘要
/// @author linqibin
/// @since 0.1.0
public record SerfileImportResult(
    int totalParsed,
    int updatedCount,
    int createdCount,
    int skippedCount,
    String serfileVersion,
    String sourceUrl,
    long durationMillis,
    String message) {

  /// 创建成功结果。
  ///
  /// @param totalParsed 解析的记录总数
  /// @param updatedCount 更新的记录数
  /// @param createdCount 新创建的记录数
  /// @param skippedCount 跳过的记录数
  /// @param serfileVersion Serfile 版本号
  /// @param sourceUrl 原始数据源 URL
  /// @param durationMillis 导入耗时（毫秒）
  /// @return 成功结果对象
  public static SerfileImportResult success(
      int totalParsed,
      int updatedCount,
      int createdCount,
      int skippedCount,
      String serfileVersion,
      String sourceUrl,
      long durationMillis) {
    return new SerfileImportResult(
        totalParsed,
        updatedCount,
        createdCount,
        skippedCount,
        serfileVersion,
        sourceUrl,
        durationMillis,
        String.format(
            "Serfile 导入完成：解析 %d 条，更新 %d 条，新建 %d 条，跳过 %d 条，耗时 %d ms",
            totalParsed, updatedCount, createdCount, skippedCount, durationMillis));
  }

  /// 创建失败结果。
  ///
  /// @param errorMessage 错误信息
  /// @param serfileVersion Serfile 版本号
  /// @param sourceUrl 原始数据源 URL
  /// @return 失败结果对象
  public static SerfileImportResult failure(
      String errorMessage, String serfileVersion, String sourceUrl) {
    return new SerfileImportResult(
        0, 0, 0, 0, serfileVersion, sourceUrl, 0, String.format("Serfile 导入失败：%s", errorMessage));
  }

  /// 判断导入是否成功。
  ///
  /// @return 如果 totalParsed > 0 则认为成功
  public boolean isSuccess() {
    return totalParsed > 0;
  }

  /// 获取实际处理的记录数（更新 + 新建）。
  ///
  /// @return 处理的记录数
  public int processedCount() {
    return updatedCount + createdCount;
  }
}
