package com.patra.catalog.adapter.scheduler.param;

/// OpenAlex Venue 导入任务参数记录。
///
/// 通过 XXL-Job 调度器以 JSON 格式传递的任务参数。
///
/// **与 MeSH 导入参数的差异**：
///
/// - Venue 不需要 URL（从 OpenAlex S3 Manifest 动态获取分区文件列表）
/// - Venue 不需要版本号（OpenAlex 使用 updated_date 分区管理版本）
///
/// JSON 格式示例：
///
/// ```json
/// {
///   "mode": "INCREMENTAL"
/// }
/// ```
///
/// 或者使用空参数（默认使用 INCREMENTAL 模式）：
///
/// ```json
/// {}
/// ```
///
/// @param mode 导入模式（可选，默认 INCREMENTAL）- INCREMENTAL 或 TRUNCATE_REIMPORT
/// @author linqibin
/// @since 0.1.0
public record VenueImportJobParam(String mode) {

  /// 创建默认参数（INCREMENTAL 模式）。
  ///
  /// @return 默认参数实例
  public static VenueImportJobParam defaults() {
    return new VenueImportJobParam("INCREMENTAL");
  }

  /// 获取模式字符串，如果为空则返回默认值。
  ///
  /// @return 模式字符串
  public String modeOrDefault() {
    return mode == null || mode.isBlank() ? "INCREMENTAL" : mode;
  }
}
