package dev.patra.identity.adapter.account.rest.response;

/// 注册、登录成功的响应体：会话令牌加上和「当前用户」接口同构的 `user`。
///
/// @param sessionToken 会话令牌，客户端原样保存，之后放进 `Authorization: Bearer`
/// @param user 账号信息
public record AuthenticatedUserResponse(String sessionToken, CurrentUserResponse user) {

  /// 创建响应。
  ///
  /// @param sessionToken 会话令牌
  /// @param user 账号信息
  /// @return 响应
  public static AuthenticatedUserResponse of(String sessionToken, CurrentUserResponse user) {
    return new AuthenticatedUserResponse(sessionToken, user);
  }

  /// 不输出令牌。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "AuthenticatedUserResponse[sessionToken=***, user=" + user + "]";
  }
}
