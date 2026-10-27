package dev.patra.interaction.domain.notification.model.vo;

/// 渲染好的摘录：纯文本，提及已换成名字，≤ 120 个扩展字素簇。
///
/// @param text 纯文本
/// @param truncated 是否截断过（门户在末尾加省略号）
public record RenderedExcerpt(String text, boolean truncated) {

  /// 构造。
  ///
  /// @param text 纯文本
  /// @param truncated 是否截断过
  /// @return 摘录
  public static RenderedExcerpt of(String text, boolean truncated) {
    return new RenderedExcerpt(text, truncated);
  }
}
