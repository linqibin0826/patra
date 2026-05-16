package dev.linqibin.patra.catalog.app.usecase.venue;

/// Venue 富化 worker loop 的完整运行统计。
///
/// LetPub 与 Scopus 两条富化管线共用的统计承载类——外层 Runner 计数、Handler 返回、
/// ScheduleJob 日志输出全部使用本 record。刻意不按管线各拆一个 result 类型：两条管线
/// 的字段完全同形同义，分离只会在 Handler 里产生零逻辑的样板翻译。若将来某条管线出现
/// 管线特有的统计字段，再派生专属 result 类型即可。
///
/// @param totalRead Reader 读取的候选 venue 总数
/// @param processed 成功完成富化的 venue 数
/// @param skipped 被 Worker 报告为 MISSING_ISSN / NOT_FOUND_IN_SOURCE 的 venue 数
/// @param failed 处理过程中抛异常被 Runner 捕获的 venue 数
/// @author linqibin
/// @since 0.1.0
public record VenueEnrichRunStats(int totalRead, int processed, int skipped, int failed) {

  /// 创建 [VenueEnrichRunStats]。
  ///
  /// @param totalRead 候选 venue 总数
  /// @param processed 成功富化数
  /// @param skipped MISSING_ISSN / NOT_FOUND_IN_SOURCE 的 venue 数
  /// @param failed 抛异常被 Runner 捕获的 venue 数
  /// @return 新建的 [VenueEnrichRunStats] 实例
  public static VenueEnrichRunStats of(int totalRead, int processed, int skipped, int failed) {
    return new VenueEnrichRunStats(totalRead, processed, skipped, failed);
  }
}
