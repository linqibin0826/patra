package dev.patra.identity.adapter.account.rest.response;

/// 发起绑定的响应体。
///
/// @param authorizationUrl 平台的授权地址，已带 `state`；门户从中取 `state` 写 Cookie，前端跳转过去
public record ProviderLinkStartedResponse(String authorizationUrl) {

  /// 创建响应。
  ///
  /// @param authorizationUrl 平台的授权地址
  /// @return 响应
  public static ProviderLinkStartedResponse of(String authorizationUrl) {
    return new ProviderLinkStartedResponse(authorizationUrl);
  }
}
