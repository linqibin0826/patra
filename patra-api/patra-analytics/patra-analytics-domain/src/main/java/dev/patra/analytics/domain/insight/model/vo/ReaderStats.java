package dev.patra.analytics.domain.insight.model.vo;

/// 一篇文献的读者数：按人长期去重，只数真人会话（埋点方案 §6.8）。
///
/// @param articleId 文献 ID
/// @param viewers 看过的人数
/// @param fulltextReaders 读过全文的人数
public record ReaderStats(long articleId, long viewers, long fulltextReaders) {

  /// 构造。
  ///
  /// @param articleId 文献 ID
  /// @param viewers 看过的人数
  /// @param fulltextReaders 读过全文的人数
  /// @return 读者数
  public static ReaderStats of(long articleId, long viewers, long fulltextReaders) {
    return new ReaderStats(articleId, viewers, fulltextReaders);
  }

  /// 没人看过。
  ///
  /// @param articleId 文献 ID
  /// @return 两个数都是 0
  public static ReaderStats none(long articleId) {
    return new ReaderStats(articleId, 0, 0);
  }
}
