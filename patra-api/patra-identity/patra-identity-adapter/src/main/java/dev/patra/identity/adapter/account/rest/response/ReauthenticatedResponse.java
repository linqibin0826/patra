package dev.patra.identity.adapter.account.rest.response;

/// 再认证成功的响应体：和登录同构，但 `sessionToken` 可能为 `null`——宽限期内没换令牌，客户端保持现有令牌。
///
/// @param sessionToken 新令牌；为 `null` 时沿用原来的
/// @param user 账号信息
public record ReauthenticatedResponse(String sessionToken, CurrentUserResponse user) {
  /// 创建响应。
  ///
  /// @param sessionToken 新令牌，没换时为 `null`
  /// @param user 账号信息
  /// @return 响应
  public static ReauthenticatedResponse of(String sessionToken, CurrentUserResponse user) {
    return new ReauthenticatedResponse(sessionToken, user);
  }

  /// 不输出令牌。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "ReauthenticatedResponse[sessionToken="
        + (sessionToken == null ? "null" : "***")
        + ", user="
        + user
        + "]";
  }
}
