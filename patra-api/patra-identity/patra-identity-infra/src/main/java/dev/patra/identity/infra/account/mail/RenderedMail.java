package dev.patra.identity.infra.account.mail;

import java.util.Objects;

/// 渲染好的一封邮件：主题、纯文本正文、HTML 正文。
///
/// @param subject 主题
/// @param text 纯文本正文
/// @param html HTML 正文
public record RenderedMail(String subject, String text, String html) {

  /// 三项都不能为 `null`。
  public RenderedMail {
    Objects.requireNonNull(subject, "subject 不能为 null");
    Objects.requireNonNull(text, "text 不能为 null");
    Objects.requireNonNull(html, "html 不能为 null");
  }

  /// 创建。
  ///
  /// @param subject 主题
  /// @param text 纯文本正文
  /// @param html HTML 正文
  /// @return 渲染结果
  public static RenderedMail of(String subject, String text, String html) {
    return new RenderedMail(subject, text, html);
  }

  /// 不输出正文（里面有链接）。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "RenderedMail[subject=" + subject + "]";
  }
}
