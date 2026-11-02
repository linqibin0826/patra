package dev.patra.analytics.domain.insight.model.vo;

import dev.patra.analytics.domain.insight.model.enums.AlsoReadSource;

/// 「看过这篇的人还看了」的一篇。
///
/// @param articleId 文献 ID
/// @param source 来源
/// @param meshTerm 命中的主要主题词原文；仅 `SAME_MESH`
/// @param venueName 期刊全称；仅 `SAME_VENUE`
public record AlsoReadItem(
    long articleId, AlsoReadSource source, String meshTerm, String venueName) {

  /// 构造。
  ///
  /// @param articleId 文献 ID
  /// @param source 来源
  /// @param meshTerm 主题词原文
  /// @param venueName 期刊全称
  /// @return 一篇
  public static AlsoReadItem of(
      long articleId, AlsoReadSource source, String meshTerm, String venueName) {
    return new AlsoReadItem(articleId, source, meshTerm, venueName);
  }

  /// 一起读的一篇。
  ///
  /// @param articleId 文献 ID
  /// @return 来源为 `CO_READ`、没有标签
  public static AlsoReadItem coRead(long articleId) {
    return new AlsoReadItem(articleId, AlsoReadSource.CO_READ, null, null);
  }
}
