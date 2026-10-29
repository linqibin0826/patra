package dev.patra.identity.adapter.account.rest.response;

import dev.patra.identity.domain.account.model.vo.profile.ProfileLink;

/// 一条链接。
///
/// @param title 标题，没有时为 `null`（门户显示域名）
/// @param url URL
public record LinkResponse(String title, String url) {

  /// 从值对象映射。
  ///
  /// @param link 链接
  /// @return 响应
  public static LinkResponse from(ProfileLink link) {
    return new LinkResponse(link.title(), link.url());
  }
}
