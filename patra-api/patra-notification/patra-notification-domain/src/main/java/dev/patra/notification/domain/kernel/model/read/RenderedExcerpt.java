package dev.patra.notification.domain.kernel.model.read;

import java.util.Objects;

/// 渲染好的摘录：提及已换成名字、已按渠道截好的纯文本。
///
/// @param text 纯文本
/// @param truncated 是否截掉了东西（站内由门户在末尾加省略号，邮件由模板加）
public record RenderedExcerpt(String text, boolean truncated) {

  /// 校验非空。
  public RenderedExcerpt {
    Objects.requireNonNull(text, "text 不能为 null");
  }

  /// 构造。
  ///
  /// @param text 纯文本
  /// @param truncated 是否截断过
  /// @return 摘录
  public static RenderedExcerpt of(String text, boolean truncated) {
    return new RenderedExcerpt(text, truncated);
  }
}
