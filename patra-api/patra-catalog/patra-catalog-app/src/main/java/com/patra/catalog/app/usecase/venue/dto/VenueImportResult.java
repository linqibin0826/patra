package com.patra.catalog.app.usecase.venue.dto;

import com.patra.catalog.domain.model.enums.DataImportMode;

/// OpenAlex Venue 导入结果（Application → Adapter）。
///
/// Venue 导入任务执行后返回给调度器的结果摘要。
///
/// @param executionId 批处理执行标识符（Spring Batch Job Execution ID）
/// @param fileCount 处理的分区文件数量
/// @param totalRecordCount manifest 中声明的总记录数
/// @param mode 使用的导入模式
/// @param message 人类可读的状态摘要
/// @author linqibin
/// @since 0.1.0
public record VenueImportResult(
    Long executionId, int fileCount, int totalRecordCount, DataImportMode mode, String message) {

  /// 创建成功结果。
  ///
  /// @param executionId 批处理执行标识符
  /// @param fileCount 分区文件数量
  /// @param totalRecordCount manifest 声明的总记录数
  /// @param mode 导入模式
  /// @return 成功结果对象
  public static VenueImportResult success(
      Long executionId, int fileCount, int totalRecordCount, DataImportMode mode) {
    return new VenueImportResult(
        executionId,
        fileCount,
        totalRecordCount,
        mode,
        String.format(
            "OpenAlex Venue 导入任务已启动，executionId=%d，fileCount=%d，totalRecordCount=%d，mode=%s",
            executionId, fileCount, totalRecordCount, mode));
  }

  /// 创建失败结果。
  ///
  /// @param mode 导入模式
  /// @param errorMessage 错误信息
  /// @return 失败结果对象
  public static VenueImportResult failure(DataImportMode mode, String errorMessage) {
    return new VenueImportResult(
        null, 0, 0, mode, String.format("OpenAlex Venue 导入任务启动失败：%s", errorMessage));
  }
}
