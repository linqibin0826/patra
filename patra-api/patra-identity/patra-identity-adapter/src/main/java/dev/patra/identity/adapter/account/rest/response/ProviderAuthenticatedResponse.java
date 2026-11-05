package dev.patra.identity.adapter.account.rest.response;

/// 第三方登录回调：老用户，已发会话。
///
/// @param result 固定 `AUTHENTICATED`
/// @param sessionToken 会话令牌
/// @param user 账号信息
public record ProviderAuthenticatedResponse(
    String result, String sessionToken, CurrentUserResponse user) {

  /// 创建响应。
  ///
  /// @param sessionToken 会话令牌
  /// @param user 账号信息
  /// @return 响应
  public static ProviderAuthenticatedResponse of(String sessionToken, CurrentUserResponse user) {
    return new ProviderAuthenticatedResponse("AUTHENTICATED", sessionToken, user);
  }

  /// 不输出令牌。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "ProviderAuthenticatedResponse[sessionToken=***, user=" + user + "]";
  }
}
