package com.patra.catalog.app.usecase.venue.scopus.command;

/// Scopus 期刊指标富化命令执行结果。
///
/// Runner 同步执行完整 worker loop 后返回的统计数据。
///
/// @param totalRead Reader 读取的候选 venue 总数
/// @param processed 成功完成富化的 venue 数
/// @param skipped 被跳过的 venue 数（ISSN-L 为空 / Scopus 未找到）
/// @param failed 处理过程中抛异常的 venue 数
/// @author linqibin
/// @since 0.1.0
public record VenueScopusEnrichResult(int totalRead, int processed, int skipped, int failed) {

  /// 创建 [VenueScopusEnrichResult]。
  ///
  /// @param totalRead Reader 读取的候选 venue 总数
  /// @param processed 成功完成富化的 venue 数
  /// @param skipped 被跳过的 venue 数
  /// @param failed 抛异常的 venue 数
  /// @return 新建的 [VenueScopusEnrichResult] 实例
  public static VenueScopusEnrichResult of(int totalRead, int processed, int skipped, int failed) {
    return new VenueScopusEnrichResult(totalRead, processed, skipped, failed);
  }
}
