package dev.patra.notification.app.mail.service;

/// 卡片外那一句后面的链接（`退订这类邮件`、`通知设置`）。
///
/// @param label 文字
/// @param url 链接
public record AsideLink(String label, String url) {

  /// 构造。
  ///
  /// @param label 文字
  /// @param url 链接
  /// @return 链接
  public static AsideLink of(String label, String url) {
    return new AsideLink(label, url);
  }
}
