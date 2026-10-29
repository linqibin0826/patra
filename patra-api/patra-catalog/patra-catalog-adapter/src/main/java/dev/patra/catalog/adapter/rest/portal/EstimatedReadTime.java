package dev.patra.catalog.adapter.rest.portal;

/// 预估阅读时长：文献详情与全文响应共用。
///
/// @author linqibin
/// @since 0.1.0
public final class EstimatedReadTime {

  private EstimatedReadTime() {}

  /// 阅读时长 `ceil(字数 / 230)`，不可读全文或字数未知时为 null。
  ///
  /// @param readable 是否可读全文
  /// @param wordCount 字数
  /// @return 分钟数
  public static Integer minutes(boolean readable, Integer wordCount) {
    if (!readable || wordCount == null) {
      return null;
    }
    return (int) Math.ceil(wordCount / 230.0);
  }
}
