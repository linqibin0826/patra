package dev.patra.analytics.domain.insight.model.vo;

/// 一对一起被读过的文献（ClickHouse 按共现规则算出，X → Y 一个方向）。
///
/// @param articleId 文献 X
/// @param relatedArticleId 文献 Y
/// @param similarity 余弦相似度
/// @param coReaders 一起读过的不同人数
public record CoReadingPair(
    long articleId, long relatedArticleId, double similarity, int coReaders) {

  /// 构造。
  ///
  /// @param articleId 文献 X
  /// @param relatedArticleId 文献 Y
  /// @param similarity 相似度
  /// @param coReaders 一起读过的人数
  /// @return 配对
  public static CoReadingPair of(
      long articleId, long relatedArticleId, double similarity, int coReaders) {
    return new CoReadingPair(articleId, relatedArticleId, similarity, coReaders);
  }
}
