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
/// **导入策略**：
///
/// 纯 INSERT 策略，用于一次性数据初始化。如果表中已有数据，导入会失败。
///
/// JSON 格式示例：
///
/// ```json
/// {}
/// ```
///
/// @author linqibin
/// @since 0.1.0
public record VenueImportJobParam() {

  /// 创建默认参数。
  ///
  /// @return 默认参数实例
  public static VenueImportJobParam defaults() {
    return new VenueImportJobParam();
  }
}
