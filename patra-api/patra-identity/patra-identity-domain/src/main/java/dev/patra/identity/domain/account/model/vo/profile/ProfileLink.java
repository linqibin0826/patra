package dev.patra.identity.domain.account.model.vo.profile;

import java.util.Objects;

/// 资料里的一条链接。
///
/// @param title 标题（规范化之后的单行文本），没有时为 `null`，门户显示域名
/// @param url 去首尾空白后的 URL，只收 `http` / `https`
public record ProfileLink(String title, String url) {

  /// 校验 URL 非空。
  public ProfileLink {
    Objects.requireNonNull(url, "url 不能为 null");
  }

  /// 工厂方法。
  ///
  /// @param title 标题，可为 `null`
  /// @param url URL
  /// @return 链接
  public static ProfileLink of(String title, String url) {
    return new ProfileLink(title, url);
  }
}
