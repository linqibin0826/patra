package dev.patra.analytics.domain.insight.model.vo;

/// 满足上榜门槛的一篇文献（ClickHouse 按热读规则算出）。
///
/// @param articleId 文献 ID
/// @param score 时间衰减后的分数
/// @param readers7d 近 7 天读过的不同人数
public record HotReadingCandidate(long articleId, double score, int readers7d) {

  /// 构造。
  ///
  /// @param articleId 文献 ID
  /// @param score 分数
  /// @param readers7d 近 7 天人数
  /// @return 候选
  public static HotReadingCandidate of(long articleId, double score, int readers7d) {
    return new HotReadingCandidate(articleId, score, readers7d);
  }
}
