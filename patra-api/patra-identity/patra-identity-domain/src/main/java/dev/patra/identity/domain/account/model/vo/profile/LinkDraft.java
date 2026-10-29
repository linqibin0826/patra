package dev.patra.identity.domain.account.model.vo.profile;

/// 请求里的一条链接，原始输入。
///
/// @param title 标题
/// @param url URL
public record LinkDraft(String title, String url) {

  /// 工厂方法。
  ///
  /// @param title 标题
  /// @param url URL
  /// @return 链接草稿
  public static LinkDraft of(String title, String url) {
    return new LinkDraft(title, url);
  }
}
